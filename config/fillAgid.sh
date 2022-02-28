# script for filling AGID in GatewayConfig.xml
# params: AGID

if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters"
fi

XML="/gateway/persistance/config/GatewayConfig.xml" 
XML_BACKUP="/gateway/persistance/config/GatewayConfig.xml.edited" 
NEW_IDENTITY="<identity>
        <!--
        AGID of the Access Point used to authenticate the gateway \(AGID string\)
        -->
        $1
    <\/identity>"
perl -i  -0pe "s/<identity>.*<\/identity>/$NEW_IDENTITY/gms" /gateway/persistance/config/GatewayConfig.xml