declare -A NETSTAT
declare -A OLD_DATA_NETSTAT

core=`lscpu | grep "^CPU(s)" | awk '{print $2}'`
numa=`lscpu | grep "^NUMA node(s)" | awk '{print $3}'`

nano_append="000000000"

interval=30
upload_rep=1
serverip=`hostname`
line_threshold=500

irq=false;
sirq=false;
ethtool=false;
softnet=false;
netstat=false;

old_total_sns=""
old_drop_sns=""
old_squeezed_sns=""
old_collision_sns=""
old_rps_sns=""
old_flow_limit_sns=""

server_name=""
cluster_name=""

influx_db=""
influx_ip=""

args=("$@")
arg=0
while [ $arg -lt ${#args[@]} ]
do
        case "${args[$arg]}" in
                -i)
                        irq=true;
                        ;;
                -interupts)
                        irq=true;
                        ;;
                -si)
                        sirq=true;
                        ;;
                -soft_interupts)
                        sirq=true;
                        ;;
                -e)
                        ethtool=true;
                        ;;
                -ethtool)
                        ethtool=true;
                        ;;
                -s)
                        softnet=true;
                        ;;
                -softnet)
                        softnet=true;
                        ;;
                -n)
                        netstat=true;
                        ;;
                -netstat)
                        netstat=true;
                        ;;
                -all)
                        irq=true;
                        sirq=true;
                        ethtool=true;
                        softnet=true;
			netstat=true;
                        ;;
                -interval)
                        arg=$(( arg+1 ))
                        value=${args[$arg]}
                        if [ $value -gt 0 ]
                        then
                                interval=$value
                        else
			        echo Invalid interval , Setting default to 30 sec.
                        fi
                        ;;
		-up)
			arg=$(( arg+1 ))
                        value=${args[$arg]}
                        if [ $value -gt 0 ]
                        then
                                upload_rep=$value
                        else
                                echo Invalid upload rep , Setting default to 1
                        fi
                        ;;
		-ip)
                        arg=$(( arg+1 ))
                        influx_ip=${args[$arg]}
                        ;;
                -db)
                        arg=$(( arg+1 ))
                        influx_db=${args[$arg]}
			echo influx db in netmonitor $influx_db
                        ;;
		-sn)
			arg=$(( arg+1 ))
			cluster_name=${args[$arg]}
			server_name=`echo $cluster_name | cut -d '-' -f 1`
			echo server name in netmonitor $server_name cluster is $cluster_name
			;;
        esac
        arg=$(( arg+1 ))
done

if [ -z "$influx_ip" ] || [ -z "$influx_db" ]
then
        echo Influx details are not present ip = $influx_ip , db = $influx_db
        exit
fi

if [ -z "$cluster_name" ] || [ -z "$server_name" ]
then
	echo Server details are not present server name = $server_name , cluster name = $cluster_name
	exit;
fi

sleep_delay_interval()
{
	start_time=`expr $1 / 1000000000`
	end_time=`date +%s%N`
	end_time=`expr $end_time / 1000000000`
	delay_time=`expr $end_time - $start_time`
	final_interval=`expr $interval - $delay_time`
	#echo sleep interval is $final_interval
	sleep $final_interval
}

process_irqs_stats()
{
	declare -A OLD_DATA_IRQS
	declare -A IRQS
	sample_count=0
	data_time=0
	while true
	do
		rep=4
		repcount=0;
		while [ $repcount -lt $rep ]
		do
			regex="eth0-TxRx"
			line=`cat "/proc/interrupts" | awk -v core=$core 'BEGIN{
				split("",temp0,";");
			}/'$regex'/{
				col_index=index($1,":");
				line_data=substr($1,0,col_index-1);
				for(i=2;i<=core+1;i++)
				{
					if(i == 2)
					{
						line_data=line_data"="$i;
					}
					else
					{
						line_data=line_data"|"$i;
					}
				}
				temp_len=length(temp0);
				temp0[temp_len]=line_data;
			}END{
				finalresult=systime()
				for(a in temp0)
				{
					finalresult=finalresult","temp0[a];
				}
				print finalresult;
			}' `
			data_time=`date +%s%N`
			single_data=(${line//,/ })
			this_time=${single_data[0]}""$nano_append
			for x in "${!single_data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				single_value=${single_data[$x]}
				data=(${single_value//=/ })
				irq_no=${data[0]}
				if [ ! -n "${OLD_DATA_IRQS[$irq_no]}" ]
				then
					OLD_DATA_IRQS[$irq_no]=${data[1]}
				else
					old_value=${OLD_DATA_IRQS[$irq_no]}
					old_array=(${old_value//|/ })
					current_value=${data[1]}
					current_array=(${current_value//|/ })
					cpu_core=0
					new_value=""
					while [ $cpu_core -lt $core ]
					do
						#echo core is $cpu_core and current value ${current_array[$cpu_core]} and old value is ${old_array[$cpu_core]}
						interval_value=`expr ${current_array[$cpu_core]} - ${old_array[$cpu_core]}`
						if [ $interval_value -ne 0 ]
						then
							if [ ! -z "$new_value" ]
							then
								new_value=$new_value","
							fi
							new_value=$new_value"CPU"$cpu_core"="$interval_value
						fi
						cpu_core=$(( cpu_core+1 ))	
					done
					if [ ! -z "$new_value" ]
					then
						IRQS[$this_time,$irq_no]=$new_value
					fi
					OLD_DATA_IRQS[$irq_no]=${data[1]}
				fi
			done
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep_delay_interval $data_time
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="interupt_stat"
			upload_data="";
			file_line_count=0;
			for x in "${!IRQS[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",irq_no="${data[1]}" "${IRQS[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_irq_1.txt" &
					upload_data="";
					file_line_count=0;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_irq_data.txt" &
			IRQS=()
			sample_count=0
		fi
		sleep_delay_interval $data_time
	done
}

process_sirqs_stats()
{
	declare -A OLD_DATA_SIRQS
	declare -A SIRQS
	sample_count=0
	data_time=0
	while true
	do
		rep=4
		repcount=0;
		while [ $repcount -lt $rep ]
		do
			regex="NET_TX|NET_RX"
			line=`cat "/proc/softirqs" | awk -v core=$core 'BEGIN{
				split("",temp0,";");
			}/'$regex'/{
				col_index=index($1,":");
				line_data=substr($1,0,col_index-1);
				for(i=2;i<=core+1;i++)
				{
					if(i == 2)
					{
						line_data=line_data"="$i;
					}
					else
					{
						line_data=line_data"|"$i;
					}
				}
				temp_len=length(temp0);
				temp0[temp_len]=line_data;
			}END{
				finalresult=systime()
				for(a in temp0)
				{
					finalresult=finalresult","temp0[a];
				}
				print finalresult;
			}' `
			data_time=`date +%s%N`
			single_data=(${line//,/ })
			this_time=${single_data[0]}""$nano_append
			for x in "${!single_data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				single_value=${single_data[$x]}
				data=(${single_value//=/ })
				sirq_no=${data[0]}
				if [ ! -n "${OLD_DATA_SIRQS[$sirq_no]}" ]
				then
					OLD_DATA_SIRQS[$sirq_no]=${data[1]}
				else
					old_value=${OLD_DATA_SIRQS[$sirq_no]}
					old_array=(${old_value//|/ })
					current_value=${data[1]}
					current_array=(${current_value//|/ })
					cpu_core=0
					new_value=""
					while [ $cpu_core -lt $core ]
					do
						#echo core is $cpu_core and current value ${current_array[$cpu_core]} and old value is ${old_array[$cpu_core]}
						interval_value=`expr ${current_array[$cpu_core]} - ${old_array[$cpu_core]}`
						if [ $interval_value -ne 0 ]
						then
							if [ ! -z "$new_value" ]
							then
								new_value=$new_value","
							fi
							new_value=$new_value"CPU"$cpu_core"="$interval_value
						fi
						cpu_core=$(( cpu_core+1 ))
					done
					if [ ! -z "$new_value" ]
					then
						SIRQS[$this_time,$sirq_no]=$new_value
					fi
					OLD_DATA_SIRQS[$sirq_no]=${data[1]}
				fi
			done
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep_delay_interval $data_time
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			upload_data="";
			file_line_count=0;
			measurement_name="soft_interupt_stat"
			for x in "${!SIRQS[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${SIRQS[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_sirq_1.txt" &
					upload_data="";
					file_line_count=0;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_sirq_data.txt" &
			SIRQS=()
			sample_count=0
		fi
		sleep_delay_interval $data_time
	done
}

process_ethtool_stats()
{
	declare -A OLD_DATA_ETHTOOL
	declare -A ETHTOOL
	declare -A ETHTOOL_Q
	sample_count=0
	data_time=0
	while true
	do
		rep=4
		repcount=0;
		while [ $repcount -lt $rep ]
		do
			regex="tx_queue|rx_queue|rx_pkts_nic|rx_bytes_nic|rx_no_dma_resources"
			line=`/usr/sbin/ethtool -S eth0 | awk 'BEGIN{
				split("",temp1,":");
			}/'$regex'/{
				value=$2;
				col_val=index($1,":");
				name=substr($1,0,col_val-1);
				type=0;
				length_temp1=length(temp1);
				if( name == "rx_pkts_nic" || name == "rx_bytes_nic" || name == "rx_no_dma_resources")
				{
					type=1;
					result=type "|" name "|" value;
					temp1[length_temp1]=result;
				}
				else
				{
					type=2;
					name_length = length(name);
					split(name,temp0,"_");
					qno=temp0[3];
					mode=temp0[1];
					index_qno=index(name,qno);
					if(qno > 9)
					{
						index_qno=index_qno+1;
					}
					else if(qno > 99)
					{
						index_qno=index_qno+2;
					}
					stat_type=substr(name,index_qno+2,name_length-index_qno+1);
					result=type "|" mode "|" value "|" qno "|" stat_type;
					temp1[length_temp1]=result;
				}
			}END{	
				finalresult=systime();
				for(a in temp1)
				{
					finalresult=finalresult","temp1[a];
				}
				print finalresult;
			}'`
			data_time=`date +%s%N`
			single_data=(${line//,/ })
			this_time=${single_data[0]}""$nano_append
			for x in "${!single_data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				single_value=${single_data[$x]}
				data=(${single_value//|/ })
				data_type=${data[0]}
				if [ $data_type -eq 1 ]
				then
					name=${data[1]}
					if [ ! -n "${OLD_DATA_ETHTOOL[$data_type,$name]}" ]
					then
						OLD_DATA_ETHTOOL[$data_type,$name]=${data[2]}
					else
						old_value=${OLD_DATA_ETHTOOL[$data_type,$name]}
						interval_value=`expr ${data[2]} - $old_value`
						if [ $interval_value -ne 0 ]
						then
							ETHTOOL[$this_time,$name]="Value="$interval_value
						fi
						OLD_DATA_ETHTOOL[$data_type,$name]=${data[2]}
					fi
				else
					mode=${data[1]}
					stat_type=${data[4]}
					q_no=${data[3]}
					#echo the data are $mode and $stat_type and $q_no
					if [ ! -n "${OLD_DATA_ETHTOOL[$data_type,$mode,$q_no,$stat_type]}" ]
					then
						OLD_DATA_ETHTOOL[$data_type,$mode,$q_no,$stat_type]=${data[2]}
					else
						old_value=${OLD_DATA_ETHTOOL[$data_type,$mode,$q_no,$stat_type]}
						interval_value=`expr ${data[2]} - $old_value`
						if [ $interval_value -ne 0 ]
						then
							ETHTOOL_Q[$this_time,$mode,$q_no,$stat_type]="Value="$interval_value
						fi
						OLD_DATA_ETHTOOL[$data_type,$mode,$q_no,$stat_type]=${data[2]}
					fi
				fi
			done
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep_delay_interval $data_time
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			upload_data="";
			file_line_count=0;
			temp_file_no=0
			measurement_name="queue_stat"
			for x in "${!ETHTOOL_Q[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",mode="${data[1]}",queue_no="${data[2]}",stat_type="${data[3]}" "${ETHTOOL_Q[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_ethtool_"$temp_file_no".txt" &
					upload_data="";
					file_line_count=0;
					temp_file_no=$(( temp_file_no+1 ))
					if [ $temp_file_no -gt 3 ]
					then
						temp_file_no=0
					fi
				fi
			done
			measurement_name="ethtool_stat"
			for x in "${!ETHTOOL[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",name="${data[1]}" "${ETHTOOL[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_ethtool_"$temp_file_no".txt" &
					upload_data="";
					file_line_count=0;
					temp_file_no=$(( temp_file_no+1 ))
					if [ $temp_file_no -gt 3 ]
					then
						temp_file_no=0
					fi
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_ethtool_data.txt" &
			ETHTOOL=()
			ETHTOOL_Q=()
			sample_count=0
		fi
		sleep_delay_interval $data_time
	done
}

process_softnet_stats()
{
	declare -A SOFTNET
	sample_count=0
	data_time=0
	while true
	do
		rep=4
		repcount=0;
		while [ $repcount -lt $rep ]
		do
			total_sns=""
			drop_sns=""
			squeezed_sns=""
			collision_sns=""
			rps_sns=""
			flow_limit_sns=""
			this_time=`date +%s%N`
			i=0
			while read total dropped squeezed j1 j2 j3 j4 j5 collision rps flow_limit_count
			do
				if [ $i -eq 0 ]
				then
					total_sns=`echo $(( 16#$total ))`
					drop_sns=`echo $(( 16#$dropped ))`
					squeezed_sns=`echo $(( 16#$squeezed ))`
					collision_sns=`echo $(( 16#$collision ))`
					rps_sns=`echo $(( 16#$rps ))`
					flow_limit_sns=`echo $(( 16#$flow_limit_count ))`
				else
					total_sns=`echo $total_sns"|"$(( 16#$total ))`
					drop_sns=`echo $drop_sns"|"$(( 16#$dropped ))`
					squeezed_sns=`echo $squeezed_sns"|"$(( 16#$squeezed ))`
					collision_sns=`echo $collision_sns"|"$(( 16#$collision ))`
					rps_sns=`echo $rps_sns"|"$(( 16#$rps ))`
					flow_limit_sns=`echo $flow_limit_sns"|"$(( 16#$flow_limit_count ))`
				fi
				i=$(( i+1 ))
			done < /proc/net/softnet_stat
			data_time=`date +%s%N`
			if [ -z $old_total_sns ]
			then
				old_total_sns=$total_sns
			else
				new_value=""
				old_array=(${old_total_sns//|/ })
				current_array=(${total_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"TOTAL"]=$new_value
				old_total_sns=$total_sns
			fi
			if [ -z $old_drop_sns ]
			then
				old_drop_sns=$drop_sns
			else
				new_value=""
				old_array=(${old_drop_sns//|/ })
				current_array=(${drop_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"DROP"]=$new_value
				old_drop_sns=$drop_sns
			fi
			if [ -z $old_squeezed_sns ]
			then
				old_squeezed_sns=$squeezed_sns
			else
				new_value=""
				old_array=(${old_squeezed_sns//|/ })
				current_array=(${squeezed_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"SQUEEZED"]=$new_value
				old_squeezed_sns=$squeezed_sns
			fi
			if [ -z $old_collision_sns ]
			then
				old_collision_sns=$collision_sns
			else
				new_value=""
				old_array=(${old_collision_sns//|/ })
				current_array=(${collision_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"COLLISION"]=$new_value
				old_collision_sns=$collision_sns
			fi
			if [ -z $old_rps_sns ]
			then
				old_rps_sns=$rps_sns
			else
				new_value=""
				old_array=(${old_rps_sns//|/ })
				current_array=(${rps_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"RPS"]=$new_value
				old_rps_sns=$rps_sns
			fi
			if [ -z $old_flow_limit_sns ]
			then
				old_flow_limit_sns=$flow_limit_sns
			else
				new_value=""
				old_array=(${old_flow_limit_sns//|/ })
				current_array=(${flow_limit_sns//|/ })
				for a in "${!current_array[@]}"
				do
					if [ ! -z "$new_value" ]
					then
						new_value=$new_value","
					fi
					interval_value=`expr ${current_array[$a]} - ${old_array[$a]}`
					new_value=$new_value"CPU"$a"="$interval_value
				done
				SOFTNET[$this_time,"FLOW_LIMIT"]=$new_value
				old_flow_limit_sns=$flow_limit_sns
			fi
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep_delay_interval $data_time
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			upload_data="";
			file_line_count=0;
			measurement_name="softnet_stat"
			for x in "${!SOFTNET[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${SOFTNET[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_softnet_1.txt" &
					upload_data="";
					file_line_count=0;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_softnet_data.txt" &
			SOFTNET=()
			sample_count=0
		fi
		sleep_delay_interval $data_time
	done
}

process_netstat_stats()
{
	sample_count=0
	data_time=0
	while true
	do
		rep=4
		repcount=0;
		while [ $repcount -lt $rep ]
		do
			regex="Udp:|Tcp:"
			line=`cat "/proc/net/snmp" | awk -v core=$core 'BEGIN{
				split("",temp0,";");
			}/'$regex'/{
				col_index=index($1,":");
				line_data=substr($1,0,col_index-1);
				if($14 != "InErrs" && $4 != "InErrors")
				{
					if(line_data == "Tcp")
					{
						line_data="tcp"
						line_data=line_data"=InErrs:"$14"|InCsumErrors:"$16
					}
					else
					{
						line_data="udp"
						line_data=line_data"=InErrors:"$4"|RcvbufErrors:"$6"|InCsumErrors:"$8;
					}
					temp_len=length(temp0);
					temp0[temp_len]=line_data;
				}
			}END{
				finalresult=systime()
					for(a in temp0)
					{
						finalresult=finalresult","temp0[a];
					}
				print finalresult;
			}' `
			data_time=`date +%s%N`
			single_data=(${line//,/ })
			this_time=${single_data[0]}""$nano_append
			for x in "${!single_data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				single_value=${single_data[$x]}
				data=(${single_value//=/ })
				network_type=${data[0]}
				if [ ! -n "${OLD_DATA_NETSTAT[$network_type]}" ]
				then
					OLD_DATA_NETSTAT[$network_type]=${data[1]}
				else
					old_value=${OLD_DATA_NETSTAT[$network_type]}
					old_array=(${old_value//|/ })
					current_value=${data[1]}
					current_array=(${current_value//|/ })
					cpu_core=0
					new_value=""
					for a in "${!current_array[@]}"
					do
						if [ ! -z "$new_value" ]
						then
							new_value=$new_value","
						fi
						temp_current_data=${current_array[$a]}
						temp_old_data=${old_array[$a]}
						current_data=(${temp_current_data//:/ })
						old_data=(${temp_old_data//:/ })
						interval_value=`expr ${current_data[1]} - ${old_data[1]}`
						new_value=$new_value""${current_data[0]}"="$interval_value
					done
					NETSTAT[$this_time,$network_type]=$new_value
					OLD_DATA_NETSTAT[$network_type]=${data[1]}
				fi
			done
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep_delay_interval $data_time
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			upload_data="";
			file_line_count=0;
			measurement_name="netstat_stat"
			for x in "${!NETSTAT[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${NETSTAT[$x]}" "${data[0]}
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netstat_1.txt" &
					upload_data="";
					file_line_count=0;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netstat_data.txt" &
			NETSTAT=()
			sample_count=0
		fi
		sleep_delay_interval $data_time
	done
}

monitor_stats()
{
	sample_count=0
	echo monitor started
	while true
	do
		rep=4
                repcount=0;
		temp_file_no=0
                while [ $repcount -lt $rep ]
		do
			run_process
			repcount=$(( repcount+1 ))
			if [ $repcount -lt $rep ]
			then
				sleep $interval
			fi
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			echo push started
			measurement_name="interupt_stat"
                        upload_data="";
			file_line_count=0;
                        for x in "${!IRQS[@]}"
                        do
				data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",irq_no="${data[1]}" "${IRQS[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
					temp_file_no=$(( temp_file_no+1 ))
					if [ $temp_file_no -gt 5 ]
					then
						temp_file_no=0
					fi
                                fi
                        done
			measurement_name="soft_interupt_stat"
                        for x in "${!SIRQS[@]}"
                        do
                                data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${SIRQS[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
                                        temp_file_no=$(( temp_file_no+1 ))
                                        if [ $temp_file_no -gt 5 ]
                                        then
                                                temp_file_no=0
                                        fi
                                fi
                        done
			measurement_name="queue_stat"
                        for x in "${!ETHTOOL_Q[@]}"
                        do
                                data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",mode="${data[1]}",queue_no="${data[2]}",stat_type="${data[3]}" "${ETHTOOL_Q[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
                                        temp_file_no=$(( temp_file_no+1 ))
                                        if [ $temp_file_no -gt 5 ]
                                        then
                                                temp_file_no=0
                                        fi
                                fi
                        done
			measurement_name="ethtool_stat"
                        for x in "${!ETHTOOL[@]}"
                        do
                                data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",name="${data[1]}" "${ETHTOOL[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
                                        temp_file_no=$(( temp_file_no+1 ))
                                        if [ $temp_file_no -gt 5 ]
                                        then
                                                temp_file_no=0
                                        fi
                                fi
                        done
			measurement_name="softnet_stat"
                        for x in "${!SOFTNET[@]}"
                        do
                                data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${SOFTNET[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
                                        temp_file_no=$(( temp_file_no+1 ))
                                        if [ $temp_file_no -gt 5 ]
                                        then
                                                temp_file_no=0
                                        fi
                                fi
                        done
			measurement_name="netstat_stat"
                        for x in "${!NETSTAT[@]}"
                        do
                                data=(${x//,/ })
                                temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",type="${data[1]}" "${NETSTAT[$x]}" "${data[0]}
                                if [ -z "$upload_data" ]
                                then
                                        upload_data=$temp_data;
                                else
                                        upload_data=$upload_data"\n"$temp_data;
                                fi
				file_line_count=$(( file_line_count+1 ))
                                if [ $file_line_count -eq $line_threshold ]
                                then
                                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor_"$temp_file_no".txt" &
                                        upload_data="";
                                        file_line_count=0;
                                        temp_file_no=$(( temp_file_no+1 ))
                                        if [ $temp_file_no -gt 5 ]
                                        then
                                                temp_file_no=0
                                        fi
                                fi
                        done
                        sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_netmonitor.txt" &
                        NETSTAT=()
			SOFTNET=()
			ETHTOOL=()
			ETHTOOL_Q=()
			IRQS=()
			SIRQS=()
                        sample_count=0
			echo push finished
		fi
		sleep $interval
	done
}

run_process()
{
	if [ $irq == true ]
        then
                process_irqs_stats & 
        fi
        if [ $sirq == true ]
        then
                process_sirqs_stats &
        fi
        if [ $softnet == true ]
        then
                process_softnet_stats &
        fi
        if [ $ethtool == true ]
        then
                process_ethtool_stats &
        fi
        if [ $netstat == true ]
        then
                process_netstat_stats &
        fi
}


main()
{
	run_process
}

main
