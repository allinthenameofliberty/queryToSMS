import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.Properties;
import java.io.UnsupportedEncodingException;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;
import java.io.LineNumberReader;
import java.io.File;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameters;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.Alphabet;

public class SmppSmsSend {
    public static void main(String[] args) {
        Properties properties = new Properties();
        FileInputStream fis = null;
        String jdbcUrl = null;
        String username = null; 
        String password = null; 
        String sqlFilePath = "sql-file.sql"; // the sql file to execute
        String smppServer = null; //
        int smppPort = 0; // = port number;
        String smppUsername = null;
        String smppPassword = null;
        String sourceAddress = null;
        String destinationNumbersFile = "destination-numbers.txt"; // Each numbers should be in new line with country code: 977
        int noOfLines = -1;
        try{
            fis = new FileInputStream("config.properties");
            properties.load(fis);
            jdbcUrl = properties.getProperty("jdbc.url"); 
            username = properties.getProperty("jdbc.username"); 
            password = properties.getProperty("jdbc.password"); 
            smppServer = properties.getProperty("smsc.host"); 
            smppPort = Integer.valueOf((String) properties.get("smsc.port")); 
            smppUsername = properties.getProperty("smsc.username"); 
            smppPassword = properties.getProperty("smsc.password");
            sourceAddress = properties.getProperty("smsc.sourceAddress"); 
        }catch (IOException e) {
            e.printStackTrace();
        }
        // Read destination numbers from file
        List<String> destinationAddresses = readDestinationNumbers(destinationNumbersFile);
        StringBuilder smsContent = new StringBuilder();
        try(LineNumberReader lineNumberReader =
            new LineNumberReader(new FileReader(new File(destinationNumbersFile)))) {
            //Skip to last line
            lineNumberReader.skip(Long.MAX_VALUE);
            noOfLines = lineNumberReader.getLineNumber();
            // System.out.println(noOfLines);
        }
        catch (IOException e){e.printStackTrace();}
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
            Statement statement = connection.createStatement();
            BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath))) {
            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sqlBuilder.append(line);
                sqlBuilder.append("\n");             }
            String sql = sqlBuilder.toString();
            ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                smsContent.append(formatResultSetRow(resultSet, "UTF-8")).append("\n");
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
        SMPPSession session = new SMPPSession();
        try {
            session.connectAndBind(smppServer, smppPort, new BindParameter(BindType.BIND_TRX, smppUsername, smppPassword, "cp", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
        } catch (IOException  e) {
            e.printStackTrace();
        }
        String message = smsContent.toString();
		Random random = new Random();
        
        byte[] utf8Bytes = null;
        try{
		utf8Bytes = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e){e.printStackTrace();}
        final int totalSegments = message.length()/152 + ((message.length() % 152 > 0) ? 1 : 0);
        int counter = 1; // counter set to tackle the throttling in SMSC
        System.out.println("Counter initialized: " + counter);
        OptionalParameter sarMsgRefNum = OptionalParameters.newSarMsgRefNum((short)random.nextInt());
        OptionalParameter sarTotalSegments = OptionalParameters.newSarTotalSegments(totalSegments);
        for (String destinationAddress : destinationAddresses) {
            for (int i = 0; i < totalSegments; i++) {
                final int seqNum = i + 1;
                int chunkSize = 152;
                String[] stringChunks = splitString(message, chunkSize);
                    OptionalParameter sarSegmentSeqnum = OptionalParameters.newSarSegmentSeqnum(seqNum);
                    sendSMS(session, smppServer, smppPort, smppUsername, smppPassword, sourceAddress, destinationAddress, stringChunks[i], sarMsgRefNum, sarSegmentSeqnum, sarTotalSegments);
            }
            System.out.println("SMS sent successfully to number: " + destinationAddress);
            if(counter % 10 == 0){
                System.out.println("Counter is a multiple of 10, sleeping for 1 seconds");
                try{
                    TimeUnit.SECONDS.sleep(1);
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
            counter += 1;
            System.out.println("Counter value is incremented: " + counter);
        }
        session.unbindAndClose();
        System.out.println("Session closed");
        counter = 1;
        System.out.println("Counter reset: " + counter);
    }
    private static List<String> readDestinationNumbers(String fileName) {
        List<String> destinationNumbers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                destinationNumbers.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return destinationNumbers;
    }
    private static void sendSMS(SMPPSession session, String smppServer, int smppPort, String smppUsername, String smppPassword, String sourceAddress, String destinationAddress, String message, OptionalParameter sarMsgRefNum, OptionalParameter sarSegmentSeqnum, OptionalParameter sarTotalSegments) {
        try{ 
            session.submitShortMessage("CMT", TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, sourceAddress, TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, destinationAddress, new ESMClass(), (byte)0, (byte)1, null, null, new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT), (byte)0, DataCodings.ZERO, (byte)0, message.getBytes(), sarMsgRefNum, sarSegmentSeqnum, sarTotalSegments);
        } catch (IOException | ResponseTimeoutException | PDUException | InvalidResponseException | NegativeResponseException e) {
            e.printStackTrace();
        }
    }
    private static String[] splitString(String input, int chunkSize) {
        int numOfChunks = (int) Math.ceil((double) input.length() / chunkSize);
        String[] output = new String[numOfChunks];

        for (int i = 0; i < numOfChunks; ++i) {
            int start = i * chunkSize;
            int end = Math.min(input.length(), start + chunkSize);
            output[i] = input.substring(start, end);
        }
        return output;
    }
    private static String formatResultSetRow(ResultSet resultSet, String characterEncoding) throws SQLException, UnsupportedEncodingException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        StringBuilder rowContent = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                rowContent.append(" "); // Add space between values
            }
            try {
                rowContent.append(new String(resultSet.getBytes(i), characterEncoding)); // Encoding message content
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return rowContent.toString();
    }
}
