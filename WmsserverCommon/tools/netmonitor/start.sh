cd "$(dirname "$0")"
netmonitor=false
sysmonitor=false

db_name="netmonitor"

check_running_script()
{
        pids=`pgrep sh -a | awk 'BEGIN{
                split("",temp0,":");
                }
                /'$1'/{
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

        if [ ! -z "$pids" ]
        then
                running=true
        fi
}

echo "Started Network Monitoring script" `date` "==" $@
server_name=$1
modules=$2
ip=$3
setup=$4
modules=(${modules//,/ })
for x in "${modules[@]}"
do
	if [ $x == "netmonitor" ]
	then
		running=false
		check_running_script "netmonitor.sh"
		if [ $running == false ]
		then
			netmonitor=true
		else
			echo Netmonitor is already running
		fi
	fi
	if [ $x == "sysmonitor" ]
	then
		running=false
                check_running_script "sysmonitor.sh"
                if [ $running == false ]
                then
			sysmonitor=true
		else
			echo Sysmonitor is already running 
		fi
	fi
done
echo server_name = $server_name setup = $setup , ip = $ip , db = $setup"_"$db_name

if [ -z "$setup" ]
then
	echo Setup not mentioned
	exit
fi

if [ -z "$ip" ]
then
	echo IP not mentioned
	exit
fi

if [ -z "$server_name" ]
then
        echo Server name not mentioned
        exit
fi

if [ $netmonitor == true ]
then
	echo netmonitor started
	nohup sh netmonitor.sh -all -db "$setup""_""$db_name" -ip "$ip" -sn "$server_name" &
fi

if [ $sysmonitor == true ]
then
	echo sysmonitor started
	nohup sh sysmonitor.sh -all -db "$setup""_""$db_name" -ip "$ip" -sn "$server_name" &
fi
