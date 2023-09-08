package membership;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class ReadResults {
    Map<String, SortedMap<String,Float>> results;

    public static final String FAULT_0 ="faults_0";
    public static final String FAULT_12 ="12<- FAULTS";
    public static final String FAULT_24 ="24<- FAULTS";
    public static final String FAULT_52 ="52<- FAULTS";

    public ReadResults(){
        results = initMap2();
        start();
    }
    private void start(){
        String protocol = "oldTcp";
        File folder = new File("/home/tsunami/Desktop/thesis_projects/experimentsResults/results/"+protocol+"Results"); // replace with actual folder path
        for (File file : folder.listFiles()) {
            if (file.isFile()) {
                //System.out.println(file.getName());
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    Float value;
                    String name;
                    if(file.getName().contains("sleep12")){
                        name = protocol+"_sleep12";
                    } else if (file.getName().contains("sleep24")) {
                        name = protocol+"_sleep24";
                    } else {
                        name = protocol+"_sleep52";
                    }
                    System.out.printf(" %s = [ ",name);
                    int n = 0;
                    List<Integer> integerList = new LinkedList<>();

                    while ((line = br.readLine()) != null) {
                        n++;
                        integerList.add(n);
                        value = getNumOther(line);
                        if(value>1.0){
                            value = 1.0f;
                        }
                        System.out.printf("%s,",value);
                        br.readLine();
                    }
                    System.out.printf(" ] ; \n");
                    System.out.printf("time_%s = %s \n",name,integerList);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private Map<String,Float> initMap(){
        Map<String,Float> mp = new HashMap<>();
        mp.put(FAULT_0,0f);
        mp.put(FAULT_12,0f);
        mp.put(FAULT_24,0f);
        mp.put(FAULT_52,0f);
        return mp;
    }
    private Map<String,SortedMap<String,Float>> initMap2(){
        Map<String,SortedMap<String,Float>> mp = new HashMap<>();
        mp.put(FAULT_0,new TreeMap<>());
        mp.put(FAULT_12,new TreeMap<>());
        mp.put(FAULT_24,new TreeMap<>());
        mp.put(FAULT_52,new TreeMap<>());
        return mp;
    }
    private Float getNum(String input){
        try{
            return Float.parseFloat(input);
        }catch (Exception e){
            return -1f;
        }
    }
    private Float getNumOther(String input){
        try{
            String [] vals = input.split("-");
            return Float.parseFloat(vals[0]);
        }catch (Exception e){
            return -1f;
        }
    }

    private Float getNumOther2(String input){
        try{
            String [] vals = input.split("-");
            return Float.parseFloat(vals[1]);
        }catch (Exception e){
            return -1f;
        }
    }

    public static void main(String [] args){
        new ReadResults();
    }
}
