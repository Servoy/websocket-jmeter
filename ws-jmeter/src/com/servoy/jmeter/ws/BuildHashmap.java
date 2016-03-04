package com.servoy.jmeter.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class BuildHashmap {
    private static final HashMap<String, String[]> reqResValues = new HashMap<String, String[]>();
    private static final String keySign ="#@#"; 
    private static final String valueSign ="##"; 

    public BuildHashmap() {}
    
    /**
     * This will read the target file as text.
     * Values are taken as keys if the line starts with a hash at hash (#@#).
     * The next line is taken as the value.
     *
     * For example:
     * #@#KeyName
     * This is the value
     *
     * Will result in a mapping of "KeyName" with value "This is the value"
     *
     * @param file
     * @throws IOException
     */
    public static void loadConfig(File file) throws IOException {
        if (file != null && file.exists()) {
            BufferedReader stream = null;
            try {
                // Open the stream
                stream = new BufferedReader(new FileReader(file));
                
                String line;
                while ((line = stream.readLine()) != null) {

                    // If we have a key add it.
                    if (line.startsWith(keySign)) {
                        String key = line.substring(keySign.length());
                        String [] value = stream.readLine().split(valueSign);
                        
                        reqResValues.put(key, value);
                    }
                }
            } finally {
                try {
                    if (stream != null)
                        stream.close();
                } catch (Exception ex) {
                }
            }
        }
    }
    
    public static HashMap<String, String[]> getHashMap(File file){
    	try {
			loadConfig(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return reqResValues;
    }
}
