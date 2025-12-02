echo started

cpu=false;
load=false;
diskio=false;
network=false;
memory=false;

interval=30
upload_rep=1
rep=4
serverip=`hostname`
line_threshold=500

influx_db=""
influx_ip=""

server_name=""
cluster_name=""

args=("$@")
arg=0
while [ $arg -lt ${#args[@]} ]
do
	case "${args[$arg]}" in
		-c)
			cpu=true;
			;;
		-cpu)
			cpu=true;
			;;
		-l)
			load=true;
			;;
		-load)
			load=true;
			;;
		-d)
			diskio=true;
			;;
		-diskio)
			diskio=true;
			;;
		-n)
			network=true;
			;;
		-network)
			network=true;
			;;
		-r)
			memory=true;
			;;
		-ram)
			memory=true;
			;;
		-all)
			cpu=true;
			load=true;
			diskio=true;
			network=true;
			;;
		-ip)
			arg=$(( arg+1 ))
                        influx_ip=${args[$arg]}
			;;
		-db)
			arg=$(( arg+1 ))
                        influx_db=${args[$arg]}
			echo influx db in sysmonitor $influx_db
                        ;;
		-interval)
			arg=$(( arg+1 ))
			value=${args[$arg]}
			if [ $value -gt 0 ]
			then
				interval=$value
			else
				echo Invalid interval , Setting default to 5 sec.
			fi
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

if [ -z $influx_ip ] || [ -z "$influx_db" ]
then
	echo Influx details are not present ip = $influx_ip , db = $influx_db
	exit
fi

if [ -z "$cluster_name" ] || [ -z "$server_name" ]
then
        echo Server details are not present server name = $server_name , cluster name = $cluster_name
        exit;
fi

mpstat_function()
{
	declare -A MPSTAT
	sample_count=0
	while true
	do
		echo mpstat start `date`
		line=`mpstat -P ALL $interval $rep | awk 'BEGIN{
		split("",temp0,":");
		split("",temp1,":");
		time=systime();
		}{
			if($1 == "" && length(temp0) != 0 )
			{
				result=time;
				for(x in temp0)
				{
					result=result"_"x":"temp0[x]
				}
				split("",temp0,":");
				len_temp1=length(temp1);
				temp1[len_temp1]=result;
			}
			else if($1 != "Linux" && $3 != "CPU" && $1 !="")
			{
				temp0[$3]="%usr="$4",%nice="$5",%sys="$6",%iowait="$7",%irq="$8",%soft="$9",%steal="$10",%guest="$11",%gnice="$12",%idle="$13;
				time=systime();
			}
		}END{
			finalresult="";
			for(a in temp1)
			{
				finalresult=finalresult""temp1[a]"|"
			}
			finalresult=substr(finalresult,0,length(finalresult)-1);
			print finalresult
		}'`
		single_data=(${line//|/ })
		for i in "${single_data[@]}"
		do
			data=(${i//_/ })
			data_time=${data[0]}
			for x in "${!data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				temp_data=${data[$x]}
				disk_data=(${temp_data//:/ })
				temp_disk=${disk_data[0]}
				temp_disk_value=${disk_data[1]}
				MPSTAT[$data_time,$temp_disk]=$temp_disk_value
			done
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="cpu_stat"
			upload_data="";
			file_line_count=0;
			for x in "${!MPSTAT[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",core_name="${data[1]}" "${MPSTAT[$x]}" "${data[0]}"000000000"
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
				file_line_count=$(( file_line_count+1 ))
				if [ $file_line_count -eq $line_threshold ]
				then
					sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_mpstat_data1.txt" &
					upload_data="";
					file_line_count=0;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_mpstat_data.txt" &
			MPSTAT=()
			sample_count=0
		fi
		echo mpstat end `date`
		sleep $interval
	done
}

loadavg_function()
{
	declare -A LOADAVG
	sample_count=0
	while true
	do
		i=0;
		while [ $i -lt $rep ]
		do
			this_time=`date +%s%N`
			line=`cat "/proc/loadavg"`
			data=(${line// / })
			LOADAVG[$this_time]="1min="${data[0]}",5min="${data[1]}",15min="${data[2]}
			i=$(( i+1 ))
			sleep $interval
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="load_stat"
			upload_data="";
			for x in "${!LOADAVG[@]}"
			do
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip" "${LOADAVG[$x]}" "$x
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_loadavg_data.txt" &
			LOADAVG=()
			sample_count=0
		fi
	done
}

memory_function()
{
	declare -A MEMORY
	sample_count=0
	while true
	do
		i=0;
		while [ $i -lt $rep ]
		do
			line=`free | awk 'BEGIN{
				str="";
			}{
				if($1 != "total")
				{
					if($1 == "Mem:")
					{
						str=str"total="$2",used="$3",free="$4",shared="$5",buff/cache="$6",available="$7
					}
					else if($1 == "Swap:")
					{
						str=str"total="$2",used="$3",free="$4
					}
					if(str != "")
					{
						str=str"|";
					}
				}
			}END{
				time=systime();
				str=substr(str,0,length(str)-1);
			        print time"_"str
			}'`
			data=(${line//_/ })
			this_time=${data[0]}
			data_mem=${data[1]}
			mem=(${data_mem//|/ })
			MEMORY[$this_time,"MEM"]=${mem[0]}
			MEMORY[$this_time,"SWAP"]=${mem[1]}
			i=$(( i+1 ))
			sleep $interval
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="ram_stat"
			upload_data="";
			for x in "${!MEMORY[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",memory_type="${data[1]}" "${MEMORY[$x]}" "${data[0]}"000000000"
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_memory_data.txt" &
			MEMORY=()
			sample_count=0
		fi
		MEMORY=()
	done
}

diskio_function()
{
	declare -A DISKIO
	sample_count=0
	devices=`ls /sys/block/`
	physical_devices=(${devices// / })
	devices=`ls /sys/devices/virtual/block`
	virtual_devices=(${devices// / })
	physical_devices=( "${physical_devices[@]/$virtual_devices}" )
	#echo ${physical_devices[@]}
	#echo ${virtual_devices[@]}
	DISKIO=()
	while true
	do
		line=`iostat -d -x $interval $rep | awk 'BEGIN{
		split("",temp0,":");
		split("",temp1,":");
		time=systime();
		}{
			if($1 == "" && length(temp0) != 0 )
			{
				result=time;
				for(x in temp0)
				{
					result=result"_"x":"temp0[x]
				}
				split("",temp0,":");
				len_temp1=length(temp1);
				temp1[len_temp1]=result;
			}
			else if($1 != "Linux" && $1 != "Device:" && $1 !="")
			{
				time=systime();
				temp0[$1]="rrqm/s="$2",wrqm/s="$3",r/s="$4",w/s="$5",rkB/s="$6",wkB/s="$7",avgrq-sz="$8",avgqu-sz="$9",await="$10",r-await="$11",w-await="$12",svctm="$13",%util="$14;
			}
		}END{
			finalresult="";
			for(a in temp1)
			{
				finalresult=finalresult""temp1[a]"|"
			}
			finalresult=substr(finalresult,0,length(finalresult)-1);
			print finalresult
		}'`
		single_data=(${line//|/ })
		for i in "${single_data[@]}"
		do
			data=(${i//_/ })
			data_time=${data[0]}
			for x in "${!data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				temp_data=${data[$x]}
				disk_data=(${temp_data//:/ })
				temp_disk=${disk_data[0]}
				temp_disk_value=${disk_data[1]}
				DISKIO[$data_time,$temp_disk]=$temp_disk_value
			done
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="diskio_stat"
			upload_data="";
			for x in "${!DISKIO[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip",disk_name="${data[1]}" "${DISKIO[$x]}" "${data[0]}"000000000"
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_diskio_data.txt" &
			DISKIO=()
			sample_count=0
		fi
		sleep $interval
	done
}

sar_function()
{
	declare -A SAR
	sample_count=0
	while true
	do
		line=`sar -n TCP,ETCP,UDP $interval $rep | awk 'BEGIN{
		split("",temp0,":");
		split("",temp1,":");
		type="NONE"
		}{
			if($1 != "Linux" && $1 != "" && $1 != "Average:")
			{
				this_time=systime();
				if($5 == "iseg/s")
				{
					type="TCP";
				}
				else if($5 == "retrans/s")
				{
					type="ETCP";
				}
				else if($3 == "idgm/s")
				{
					type="UDP";
				}
				else if(type == "TCP")
				{
					temp0[type][this_time]="iseg/s="$5",oseg/s="$6;
				}
				else if(type == "ETCP")
				{
					temp0[type][this_time]="retrans/s="$5",isegerr/s="$6;
				}
				else if(type == "UDP")
				{
					temp0[type][this_time]="idgm/s="$3",odgm/s="$4;
				}
			}
		}END{
			finalresult="";
			for(i in temp0)
			{
				result=i;
				for(j in temp0[i])
				{
					result=result"_"j":"temp0[i][j];
				}
				len_temp1=length(temp1);
				temp1[len_temp1]=result;
			}
			for(a in temp1)
			{
				finalresult=finalresult""temp1[a]"|"
			}
			finalresult=substr(finalresult,0,length(finalresult)-1);
			print finalresult
		}'`
		single_data=(${line//|/ })
		for i in "${single_data[@]}"
		do
			data=(${i//_/ })
			data_type=${data[0]}
			for x in "${!data[@]}"
			do
				if [ $x -eq 0 ]
				then
					continue;
				fi
				temp_data=${data[$x]}
				sub_data=(${temp_data//:/ })
				data_time=${sub_data[0]}
				data_value=${sub_data[1]}
				SAR[$data_time,$data_type]=$data_value
			done
		done
		sample_count=$(( sample_count+1 ))
		if [ $sample_count -eq $upload_rep ]
		then
			measurement_name="network_stat"
			upload_data="";
			for x in "${!SAR[@]}"
			do
				data=(${x//,/ })
				temp_data=$measurement_name"_"${data[1]}",server_name="$server_name",cluster_name="$cluster_name",server_ip="$serverip" "${SAR[$x]}" "${data[0]}"000000000"
				if [ -z "$upload_data" ]
				then
					upload_data=$temp_data;
				else
					upload_data=$upload_data"\n"$temp_data;
				fi
			done
			sh updateinflux.sh "$influx_ip" "$influx_db" "$upload_data" "influx_sar_data.txt" &
			SAR=()
			sample_count=0
		fi
		sleep $interval
	done
}

main()
{
	if [ $cpu == true ]
	then
		mpstat_function & 
	fi
	if [ $load == true ]
	then
		loadavg_function & 
	fi
	if [ $diskio == true ]
	then
		diskio_function & 
	fi
	if [ $network == true ]
	then
		sar_function &
	fi
	if [ $memory == true ]
	then
		memory_function &
	fi
}

main
