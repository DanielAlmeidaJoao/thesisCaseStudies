import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
public class InfoFileReader {
    public static void main(String[] args) {
        try {
            readMetrics(args);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public static int countInfoFiles(File [] files){
        int c = 0;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".info")) {
                c++;
            }
        }
        return c;
    }

    public static void readMetrics(String [] args) throws Exception{
        String fileName=args[0];
        String faults = args[1];
        int processes = Integer.parseInt(args[2]);
        int diffCount = 0;

        StringBuilder stringBuilder = new StringBuilder();

        while(true){
            Thread.sleep(1000);

            File folder = new File("./logs/"); // replace with actual folder path
            int sentCount = 0;
            int receivedCount = 0;
            int infoFiles = 0;
    
            //System.out.println("FILES INFO "+folder.listFiles().length);

            for (File file : folder.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".info")) {
                    infoFiles++;
                    
                    
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.contains("Sent:")) {
                                sentCount++;
                            } else if (line.contains("Received:")) {
                                receivedCount++;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            int EXPECTED_SENT_COUNT = ((sentCount/infoFiles)*processes);
            int EXPECTED_TO_RECEIVE = (processes*EXPECTED_SENT_COUNT);
            //            float reliability = rec/exp;


            if(EXPECTED_TO_RECEIVE==0){
                continue;
            }

            /**
            
            if(processes!=effectiveInfoFiles){
                System.out.printf("PROCESSEZ %d; EXPECTED_TO_RECEIVE %d. \n",infoFiles,EXPECTED_TO_RECEIVE);
                System.out.println("Total messages sent: " + sentCount);
                System.out.println("Total messages received: " + receivedCount);
            } **/
                
            //String fileName = "results.txt";
 
            float exp = EXPECTED_TO_RECEIVE;
            float rec = receivedCount;
            float reliability = rec/exp;
    
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
                writer.append(reliability+"-"+sentCount+"-"+receivedCount);
                writer.newLine();
                writer.append(faults+"<- FAULTS");
                writer.newLine();
                stringBuilder.append("Appended to file ").append(fileName).append(" ")
                .append(receivedCount).append(" ").append(reliability);
                System.out.println(stringBuilder.toString());
                stringBuilder.delete(0,stringBuilder.length());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
}