import java.io.*;

public class ReadStreamAppLogs {

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

        StringBuilder stringBuilder = new StringBuilder();
        long start = System.currentTimeMillis();
        while(true){
            Thread.sleep(1000);

            File folder = new File("./logs/"); // replace with actual folder path
            int sentCount = 0;
            int receivedCount = 0;
            int msgReceivedCount = 0;

            for (File file : folder.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".info")) {
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.contains("FILE SENT")) {
                                sentCount++;
                            } else if (line.contains("FILE DELIVERED")) {
                                receivedCount++;
                                if(line.contains("greater")){
                                    System.out.println("PORRRRRRRRAS PORRRRAS");
                                }
                            } else if(line.contains("Received")){
                                msgReceivedCount++;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
                writer.append(sentCount+"-"+receivedCount);
                writer.newLine();
                long elapsed = (System.currentTimeMillis() - start);
                writer.append("ELAPSED:"+elapsed);
                System.out.println("ELAPSED: "+elapsed+" sent "+sentCount+" received: "+receivedCount+" msgReceived: "+msgReceivedCount);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(receivedCount==(processes-1)){
                Runtime.getRuntime().exec("./stopAll.sh");
            }
        }

    }
}
