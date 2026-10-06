<center><b>queryToSMS</b></center><br/>
This is a simple java applicaiton to connect to a postgres database and SMPP server. The purpose is to query data using JDBC and send the colleceted report as a SMS to different numbers.
Since this is older version of java that is used with following dependencies, the below shall be used to compile the code.
<br/><br/>
<i>javac -source 8 -target 8 -cp "<path\to>\jsmpp-3.0.1.jar;<path\to>\slf4j-api-1.7.36.jar;." SmppSmsSend.java</i><br/><br/>
<li/>sql query should be written and saved in the file sql-file.sql<br/>
<li/>recipient number shall be entered in the file destination-numbers.txt, line separated with country code.<br/>
<li/>other configuraitons on config.properties. like jdbc connection parameters/ smpp connection parameters, etc..<br/>
<li/>the configuration files are :
 <ul> <li/>jdbc.url = jdbc:postgresql://<IP>:<PORT>/<DBName>
      <li/>jdbc.username = <User>
<li/>jdbc.password = <Password>

<li/>smsc.host = <SMSC SERVER IP>
<li/>smsc.port = <SMSC SERVER PORT>
<li/>smsc.username = <SMPP USER>
<li/>smsc.password = <SMPP PASSWORD>
<li/>smsc.sourceAddress = <SMPP SENDER NAME>
</SMPP>ul
  <br/>
If using different database, need appropriate jdbc jar file and modification in the java codes accordingly.
<br/>
This code uses splitting message into segments using SAR segments. If using UHD, need different approach.
