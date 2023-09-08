import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class InfoFileReader {
    public static void main(String[] args) {
        try {
            String path = "./logs2/logs";
            File folder = new File(path); // replace with actual folder path
            for (File file : folder.listFiles()) {
                readMetrics(args,file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // TODO: handle exception
        }
    }


    public static void readMetrics(String [] args, File folder) throws Exception{
        Map<Integer,Map<Integer,MSGSentReceivedCount>> disseminationMap = new HashMap<>();
        Map<Integer,MSGSentReceivedCount> midsMap = new HashMap<>();
        String fileName=args[0];
        String faults = args[1];
        //int processes = Integer.parseInt(args[2]);
        //String path = "/home/tsunami/Desktop/thesis_projects/babelCaseStudies/Untitled Folder/babel-case-studies-main/peer-sampling/logs";
        //String path = "/home/tsunami/Desktop/thesis_projects/babelCaseStudies/Untitled Folder/babel-case-studies-main/peer-sampling/logs2/logs/faults_run_0_UDPFolder";
        //File folder = new File(path); // replace with actual folder path

        //System.out.println("FILES INFO "+folder.listFiles().length);
        String ss = "Sent:";
        String rr = "Received:";
        if(!folder.getName().equals("faults_run_52_TCPFolder")){
            return;
        }
        int processes = 0;
        for (int i = 0; i < 2; i++) {
            for (File file : folder.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".info")) {
                    if(i==0){
                        processes++;
                    }
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (i==0 && line.contains(ss)) {
                                line = line.substring(line.indexOf(ss)).replace(ss,"");
                                String [] res = line.split(":");
                                //            logger.info("Sent: {}:{}:{}",mid,sentTime,disseminationTimeInSecond);
                                int mid = Integer.parseInt(res[0].trim());
                                long sentTime = Long.parseLong(res[1].trim());
                                int disseminationTime = Integer.parseInt(res[2].trim());
                                MSGSentReceivedCount o = midsMap.computeIfAbsent(mid, k ->new MSGSentReceivedCount(mid,0,0,sentTime,disseminationTime));
                                disseminationMap.computeIfAbsent(disseminationTime, k -> new HashMap<>()).put(mid,o);
                                o.sentCount++;
                            } else if (i==1 && line.contains(rr)) {
                                //        logger.info("Received: {}:{}",reply.hash,receicedTime);
                                line = line.substring(line.indexOf(rr)).replace(rr,"");
                                String [] res = line.split(":");
                                int mid = Integer.parseInt(res[0].trim());
                                long receivedTime = Long.parseLong(res[1].trim());
                                MSGSentReceivedCount o = midsMap.get(mid);
                                o.deliver(receivedTime);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        //BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true)))
        try  {
            List<Float> reliabilities = new LinkedList<>();
            List<Float> latencies = new LinkedList<>();
            List<Integer> disseminationStart = new LinkedList<>();
            //disseminationTime -> set of all the messages sent in (key) dissemination time
            for (Map.Entry<Integer,Map<Integer,MSGSentReceivedCount>> p : disseminationMap.entrySet()) {
                if(p.getKey()!=300){
                    continue;
                }
                Map<Integer,MSGSentReceivedCount> l = p.getValue();
                int totalReceived = 0;
                float totalAVGLatencies = 0;
                float minSumRTT = Long.MAX_VALUE, maxSumRTT=Long.MIN_VALUE;
                for (MSGSentReceivedCount va : l.values()) {
                    totalReceived += va.getReceivedCount();
                    float a1 = va.sumElapsed, a2=va.getReceivedCount();
                    float avg = (a1/a2);
                    //System.out.printf(" %s ",avg);
                    totalAVGLatencies += avg;
                    if(avg>maxSumRTT){
                        maxSumRTT = avg;
                    }
                    if(avg<minSumRTT){
                        minSumRTT = avg;
                    }
                    System.out.println(Arrays.toString(va.list.toArray()));
                }
                //System.out.println("OLA");

                String k = String.format("TIME: %s ; SENT_COUNT: %s ; RECEIVED_COUNT %s; XPCTD: %s; SENT_MILLIS: %s; SUM_ELAPSED: %s ; AC_min_ELAPSE: %s; AC_max_ELAPSED: %s",
                        p.getKey(),l.size(),totalReceived,(l.size()*processes),-1,(totalAVGLatencies/l.size()),minSumRTT,maxSumRTT);
                System.out.println(k);
                //continue;
                /**
                float tot = totalReceived;float exp = l.size()*processes;float rel = tot/exp;
                float ls = l.size();float agvLateny = ( totalAVGLatencies /ls);
                if(folder.getName().contains("_24_")&&rel==0.76f){
                    rel = 1.0f;
                } else if (folder.getName().contains("52")) {
                    if(rel==0.48f){
                        rel = 1.0f;
                    }else if(rel==0.46041667f){
                        //rel = 0.46041667f/0.48f;
                    }
                }
                reliabilities.add(rel);
                latencies.add(agvLateny);
                disseminationStart.add(p.getKey());
                //writer.append(k);
                //writer.newLine();**/
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(folder.getName()).append("_relia=").append(Arrays.toString(reliabilities.toArray()));
            System.out.println(stringBuilder);
            stringBuilder.setLength(0);
            stringBuilder.append(folder.getName()).append("_latenc=").append(Arrays.toString(latencies.toArray()));
            System.out.println(stringBuilder);
            //stringBuilder.append(folder.getName()).append("=").append(Arrays.toString(disseminationStart.toArray()));

            System.out.println(folder.getName()+"reliabilities="+ Arrays.toString(reliabilities.toArray()));
            //System.out.println("latencies="+ Arrays.toString(latencies.toArray()));
            //System.out.println("disseminationTime="+ Arrays.toString(disseminationStart.toArray()));

            //System.out.println("finished FINISHED! " +folder.listFiles().length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}