
if [ $# -eq 0 ]; then
  echo "Usage: initialize-config.sh <config service IP> <rest api home>"
  exit 0
fi


curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/A/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/B/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/C/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/D/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/E/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/F/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/G/hcd.conf -v -s --max-time 10 --no-keepalive
curl -X POST -d @$2/conf/axis-default.json http://$1:4000/config/tmt/aps/ics/galil/H/hcd.conf -v -s --max-time 10 --no-keepalive

