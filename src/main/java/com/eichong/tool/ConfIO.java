package com.eichong.tool;

import com.alibaba.fastjson.JSON;
import org.apache.logging.log4j.util.Strings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ConfIO {

    static Charset charset_ = Charset.forName("UTF-8");
    private Path file;
    private String json;
    private Object lock = new Object();

    private String getDataParentPath() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();

        System.out.println("Code source localtion: " + path);
        if (path.indexOf("file:") == 0) {
            path = path.substring(5);
        }

        if(System.getProperty("os.name").contains("dows")) {
            path = path.substring(1,path.length());
        }

        if(path.contains("jar")) {
            path = path.substring(0,path.lastIndexOf("."));
            return path.substring(0,path.lastIndexOf("/"));
        }
        return path.replace("target/classes/", "");
    }

    public String getDataPath() {
        String parentPath = getDataParentPath();
        if (parentPath.charAt(parentPath.length()-1) != '/')
            parentPath += "/";
        String dataPathStr = parentPath + "redisPlus-data/";
        System.out.println("dataPathStr: " + dataPathStr);

        Path datafPath = Paths.get(dataPathStr);
        if (!Files.exists(datafPath)) {
            try {
                Files.createDirectory(datafPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dataPathStr;
    }

    public String getConfPath() {
        String dataPath = getDataPath();
        String confPath = dataPath + "conf/";
        Path conf = Paths.get(confPath);
        if (!Files.exists(conf)) {
            try {
                Files.createDirectory(conf);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return confPath;
    }

    public String getDownloadPath() {
        String dataPath = getDataPath();
        String downloadPath = dataPath + "download/";
        Path download = Paths.get(downloadPath);
        if (!Files.exists(download)) {
            try {
                Files.createDirectory(download);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return downloadPath;
    }

    public void load(String fileName) {
        synchronized (lock) {
            String confPath = getConfPath();
            file = Paths.get(confPath + fileName);
            if (!Files.exists(file)) {
                try {
                    Files.createFile(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            StringBuilder strBuf = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(file, charset_)) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    strBuf.append(line);
                    strBuf.append("\n");
                }
            } catch (IOException x) {
                System.err.format("IOException: %s%n", x);
            }

            json = strBuf.toString();
        }
    }

    public <T> List<T> query(Class<T> clazz) {
        synchronized (lock) {
            if (Strings.isEmpty(json))
                return new LinkedList<T>();
            List<T> l = JSON.parseArray(json, clazz);
            if (l == null)
                return new LinkedList<T>();
            return l;
        }
    }

    public <T> int update(Comparable<T> toDelete, T toAdd, Class<T> clazz) {
        synchronized (lock) {
            List<T> l;
            if (Strings.isEmpty(json))
                l = new LinkedList<>();
            else
                l = JSON.parseArray(json, clazz);

            if (toDelete != null && l != null && !l.isEmpty()) {
                Iterator<T> it = l.iterator();
                while (it.hasNext()) {
                    T c = it.next();
                    if (toDelete.compareTo(c) == 0) {
                        it.remove();
                        break;
                    }
                }
            }

            if (toAdd != null) {
                l.add(toAdd);
            }

            json = JSON.toJSONString(l);
            return writeConfFile();
        }
    }

    private int writeConfFile() {
        try (BufferedWriter writer = Files.newBufferedWriter(file, charset_)) {
            writer.write(json, 0, json.length());
            return 1;
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
        return 0;
    }

}