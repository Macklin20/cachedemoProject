echo -e $3 > $4
echo ip $1 db $2 file $4
curl -H 'Content-Type: text/plain' -XPOST 'http://'"$1"'/write?db='"$2"'' --data-binary "@$4"
