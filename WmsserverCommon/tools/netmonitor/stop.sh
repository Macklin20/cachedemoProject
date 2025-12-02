stop_after=0

system=false;
network=false;

echo "Stopped Network Monitoring script" `date` "==" $@

modules=$1
modules=(${modules//,/ })
for x in "${modules[@]}"
do
	if [ $x == "netmonitor" ]
	then
		network=true;
	fi
	if [ $x == "sysmonitor" ]
	then
		system=true;
	fi
done

args=("$@")
arg=1
while [ $arg -lt ${#args[@]} ]
do
	case "${args[$arg]}" in
		-system)
			system=true;
			;;
		-s)
			system=true;
			;;
		-network)
			network=true;
			;;
		-n)
			network=true;
			;;
		-all)
			system=true;
			network=true;
			;;
		-after)
			arg=$(( arg+1 ))
			value=${args[$arg]}
			if [ $value -gt 0 ]
			then
				stop_after=$value
			fi
			;;
	esac
	arg=$(( arg+1 ))
done

sleep $stop_after

regex="";

if [ $system == true ]
then
	if [ ! -z "$regex" ]
	then
		regex=$regex"|";
	fi
	regex=$regex"sysmonitor.sh";
fi
if [ $network == true ]
then
	if [ ! -z "$regex" ]
	then
		regex=$regex"|";
	fi
	regex=$regex"netmonitor.sh";
fi

#echo the regex is $regex

if [ -z "$regex" ]
then
	echo exiting
	exit;
fi

pids=`pgrep sh -a | awk 'BEGIN{
	split("",temp0,":");
	}
	/'$regex'/{
		len_temp=length(temp0);
		temp0[len_temp]=$1;
	}END{
		finalresult="";
		for(a in temp0)
		{
			finalresult=finalresult""temp0[a]","
		}
		finalresult=substr(finalresult,0,length(finalresult)-1);
		print finalresult
	}'`
echo $pids
single_data=(${pids//,/ })
for i in "${single_data[@]}"
do
	kill $i
done

echo stopped - $pids
