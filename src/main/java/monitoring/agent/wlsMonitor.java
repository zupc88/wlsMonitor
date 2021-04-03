package monitoring.agent;

import java.io.IOException;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import java.util.Calendar;
import java.io.*;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.ReflectionException;

import com.rsa.sslj.x.S;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray; //JSON배열 사용
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class wlsMonitor {
    private static MBeanServerConnection connection;
    private static JMXConnector connector;
    private static final ObjectName service;
    private static String servers[] = new String[40];
    private static int monitoringInstanceCount;
    private Map mBeanMap = new HashMap();
    private Map jvmRuntimeMap = new HashMap();
    private Map serverRuntimeMap = new HashMap();
    private Map threadPoolRuntimeMap = new HashMap();
    private Map jdbcDataSourceRuntimeMap = new HashMap();
    private ObjectName[] serverRT;
    JSONObject monitorResultJson = new JSONObject();

    static {
        try {
            service = new ObjectName("com.bea:Name=DomainRuntimeService,Type=weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean");
        }
        catch (MalformedObjectNameException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    public static String TimeStamp() {
        Calendar cal = Calendar.getInstance();

        String time = String.format("<%04d.%02d.%02d %02d:%02d:%02d>",
                cal.get(Calendar.YEAR),
                (cal.get(Calendar.MONTH) + 1),
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND)
        );
        return time;
    }

    public static String TimeStamp2() {
        Calendar cal = Calendar.getInstance();

        String time = String.format("[%02d.%02d_%02d:%02d]",
                (cal.get(Calendar.MONTH) + 1),
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
        );
        return time;
    }

    public static void LoadServer()
    {
        try
        {
            BufferedReader in = new BufferedReader(new FileReader("wls_server_info.ini"));
            String s;

            int count=0;
            while ((s = in.readLine()) != null)
            {
                servers[count]=s;
                count++;
            }
            monitoringInstanceCount=count;
            in.close();
        } catch (IOException e)
        {
            System.err.println(e);
            System.exit(1);
        }
    }

    public void initConnection(String hostname, String portString, String username, String password) throws Exception {
        try {
            if (hostname == null)
                hostname = "127.0.0.1";
            if (portString == null)
                portString = "7001";
            if (username == null)
                username = "weblogic";
            if (password == null)
                password = "weblogic";

            String protocol = "t3";
            Integer portInteger = Integer.valueOf(portString);
            int port = portInteger.intValue();
            String jndiroot = "/jndi/";
            String mserver = "weblogic.management.mbeanservers.domainruntime";
            JMXServiceURL serviceURL = new JMXServiceURL(protocol, hostname, port,jndiroot + mserver);
            Hashtable h = new Hashtable();
            h.put(Context.SECURITY_PRINCIPAL, username);
            h.put(Context.SECURITY_CREDENTIALS, password);
            h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
            h.put("jmx.remote.x.request.waiting.timeout", new Long(10000));
            connector = JMXConnectorFactory.connect(serviceURL, h);
            connection = connector.getMBeanServerConnection();
            serverRT = getServerRuntimes();
        } catch(IOException e)
        {

            System.out.println("Admin Server is NOT running!");

            String time = TimeStamp2();
            BufferedWriter LOG_FILE = new BufferedWriter(new FileWriter("c:\\infapp\\mw.log", true));
            String SMS_MESSAGE = String.format("%sAdminServer:STATE=DOWN", time);
            LOG_FILE.write(SMS_MESSAGE);
            LOG_FILE.newLine();

            LOG_FILE.close();
        }
    }

    public static ObjectName[] getServerRuntimes() throws Exception {
        return (ObjectName[]) connection.getAttribute(service, "ServerRuntimes");
    }

    /************************
     * 1. Generate JVMRuntime
     ***********************/
    private ObjectName getJVMRuntime(ObjectName obj) throws Exception {
        ObjectName JVMRmb = (ObjectName) connection.getAttribute(obj,"JVMRuntime");
        return JVMRmb;
    }

    /**************************
     * 2. Get ThreadPoolRuntime
     **************************/
    private ObjectName getThreadPoolRuntime(ObjectName obj) throws Exception {
        ObjectName threadPoolRmb = (ObjectName) connection.getAttribute(obj,"ThreadPoolRuntime");
        return threadPoolRmb;
    }

    /***************************************
     * 3. Generate JDBCDataSourceRuntime
     ***************************************/
    private ObjectName getJDBCDatasourceRuntime(ObjectName obj)
            throws Exception {
        ObjectName JVMRmb = (ObjectName) connection.getAttribute(obj,"JDBCDataSourceRuntime");
        return JVMRmb;
    }

    private ObjectName getJDBCDriverParamsBean(ObjectName jdbcSystemResourceMBean)
            throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException,
            IntrospectionException {
        ObjectName jdbcDataSourceBean = (ObjectName) getObjectName(
                jdbcSystemResourceMBean, "JDBCResource");

        return (ObjectName) getObjectName(jdbcDataSourceBean, "JDBCDriverParams");
    }

    private Object getObjectName(ObjectName objectName, String attributeName)
            throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {
        return connection.getAttribute(objectName, attributeName);
    }
    /************************************************
     * 4. Get Mbean object by name and parent MBean
     ************************************************/
    private ObjectName getRuntimeMbean(ObjectName parentMbeanObj, String sonMbeanObj) throws Exception {
        if (connection != null) {
            ObjectName rmb = (ObjectName) connection.getAttribute(parentMbeanObj, sonMbeanObj);
            return rmb;
        } else
            return null;
    }

    /*******************************************************************
     * 5. Iterate through given Mbean and get wanted attribute values
     ******************************************************************/
    public Map getMBeanInfoMap(Vector<String> attrVec, String mBeanTpe)
            throws Exception {
        return null;
    }

    public void CheckInstanceState() throws Exception {
        int length = (int) serverRT.length;
        String name = new String();
        String state = new String();
        Boolean runningFlag = false;

        for (int j = 0; j < monitoringInstanceCount; j++) {
            for (int i = 0; i < length; i++) {
                name = (String) connection.getAttribute(serverRT[i], "Name");
                state = (String) connection.getAttribute(serverRT[i], "State");

                if(servers[j].equals(name)) {
                    runningFlag = true;
                }
            }
            if(runningFlag == true)
            {
                String time = TimeStamp();
                String SERVER_STATE =  String.format("%s [NOTICE-%s] %s %s", time, servers[j], servers[j], "is RUNNING!!!!");
                runningFlag = false;
            }
            else
            {
                String time = TimeStamp();
                String SERVER_STATE =  String.format("%s [CRITICAL-%s] %s %s", time, servers[j], servers[j], "is DOWN!!!! <---");
                System.out.println(SERVER_STATE);

                String time2 = TimeStamp2();
                BufferedWriter LOG_FILE = new BufferedWriter(new FileWriter("c:\\infapp\\mw.log", true));
                String SMS_MESSAGE = String.format("%s%s:STATE=DOWN", time2, servers[j]);
                LOG_FILE.write(SMS_MESSAGE);
                LOG_FILE.newLine();
                LOG_FILE.close();

                runningFlag = false;
            }
        }
    }

    // WebLogic Thread 상태 체크
    public void getThreadHealthCheck(ObjectName on) throws Exception
    {
        String TIME = TimeStamp();
        String SERVER_NAME= new String();
        String threadIdle = new String();
        String activeThreads = new String();
        String stuckThreads = new String();
        String throughput = new String();
        String MESSAGE = new String();

        SERVER_NAME = (String) connection.getAttribute(on, "Name");
        threadIdle= connection.getAttribute(getThreadPoolRuntime(on),"ExecuteThreadIdleCount").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Execute Thread Idle :", threadIdle);
        System.out.println(MESSAGE);

        if(Integer.parseInt(threadIdle) < 2)
        {
            String time2 = TimeStamp2();

            BufferedWriter LOG_FILE = new BufferedWriter(new FileWriter("c:\\infapp\\mw.log", true));
            String SMS_MESSAGE = String.format("%s%s:IdleThread=%s", time2, SERVER_NAME, threadIdle);
            LOG_FILE.write(SMS_MESSAGE);
            LOG_FILE.newLine();
            LOG_FILE.close();
        }

        activeThreads= connection.getAttribute(getThreadPoolRuntime(on),"ExecuteThreadTotalCount").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Active Execute Threads :", activeThreads);
        System.out.println(MESSAGE);

        stuckThreads = connection.getAttribute(getThreadPoolRuntime(on), "StuckThreadCount").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "StuckThreadCount  :", stuckThreads);
        System.out.println(MESSAGE);

        throughput = connection.getAttribute(getThreadPoolRuntime(on), "Throughput").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Throughput  :", throughput);
        System.out.println(MESSAGE);

        JSONObject threadResultJon = new JSONObject();
        threadResultJon.put("ExecuteThreadIdleCount", threadIdle);
        threadResultJon.put("ExecuteThreadTotalCount", activeThreads);
        threadResultJon.put("StuckThreadCount", stuckThreads);
        threadResultJon.put("throughput", throughput);
        monitorResultJson.put("thread", threadResultJon);
    }

    public void getHeapMemoryHealthCheck(ObjectName on) throws Exception
    {
        String TIME = TimeStamp();
        String resourceValue = new String();
        String SERVER_NAME= new String();
        String MESSAGE = new String();
        String heapFreePercent = new String();
        String heapSizeCurrent = new String();
        String heapFreeCurrent = new String();
        String heapSizeMax = new String();

        SERVER_NAME = (String) connection.getAttribute(on, "Name");
        heapFreePercent = connection.getAttribute(getJVMRuntime(on),"HeapFreePercent").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Heap Free Percent(%): ",  heapFreePercent);
        System.out.println(MESSAGE);

        heapSizeCurrent = connection.getAttribute(getJVMRuntime(on),"HeapSizeCurrent").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Heap Size Current: ",  heapSizeCurrent);
        System.out.println(MESSAGE);

        heapFreeCurrent = connection.getAttribute(getJVMRuntime(on),"HeapFreeCurrent").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Heap Free Current: ",  heapFreeCurrent);
        System.out.println(MESSAGE);

        heapSizeMax = connection.getAttribute(getJVMRuntime(on),"HeapSizeMax").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Heap Max Size: ",  heapSizeMax);
        System.out.println(MESSAGE);

        JSONObject heapMemoryResultJon = new JSONObject();
        heapMemoryResultJon.put("heapFreePercent", heapFreePercent);
        heapMemoryResultJon.put("heapSizeCurrent", heapSizeCurrent);
        heapMemoryResultJon.put("heapFreeCurrent", heapFreeCurrent);
        heapMemoryResultJon.put("heapSizeMax", heapSizeMax);
        monitorResultJson.put("heapMemory", heapMemoryResultJon);

        JSONArray datasourceArray = new JSONArray();
        JSONObject datasourceObject = new JSONObject();
        datasourceObject.put("test1", "1");
        datasourceObject.put("test2", "2");
        datasourceObject.put("test3", "3");
        datasourceArray.add(datasourceObject);
        datasourceObject.put("te234", "1");
        datasourceObject.put("tes234", "2");
        datasourceObject.put("tes423", "3");
        datasourceArray.add(datasourceObject);
        monitorResultJson.put("datasource", datasourceArray);

    }

    public void getDBPoolHealthCheck(ObjectName on) throws Exception {
        String TIME = TimeStamp();
        String resourceValue = new String();
        String SERVER_NAME= new String();
        String NUM_AVAILABLE = new String();
        String MESSAGE = new String();

        SERVER_NAME = (String) connection.getAttribute(on, "Name");
        ObjectName[] appRT = (ObjectName[]) connection.getAttribute(
                new ObjectName("com.bea:Name=" + SERVER_NAME + ",ServerRuntime="
                        + SERVER_NAME + ",Location=" + SERVER_NAME
                        + ",Type=JDBCServiceRuntime"),
                "JDBCDataSourceRuntimeMBeans");
        int appLength = (int) appRT.length;

        if (appLength == 0) {
            resourceValue = "null";
            MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "DB Connection Pool: ",  resourceValue);
            System.out.println(MESSAGE);
        }

        for (int x = 0; x < appLength; x++) {
            String NAME = (String) connection.getAttribute(appRT[x], "Name");
            String PRE_INFO = String.format("%s [NOTICE-%s] %s: ", TIME, SERVER_NAME, NAME);
            System.out.println(PRE_INFO + "ConnectionsTotalCount:         " + connection.getAttribute(appRT[x],"ConnectionsTotalCount"));

            NUM_AVAILABLE = connection.getAttribute(appRT[x],"NumAvailable").toString();
            System.out.println(PRE_INFO + "NumAvailable:                  " + NUM_AVAILABLE);

            if(Integer.parseInt(NUM_AVAILABLE) < 5)
            {
                String time = TimeStamp();
                String time2 = TimeStamp2();
                String SERVER_STATE =  String.format("%s [CRITICAL-%s] %s %s", time, SERVER_NAME, NAME, " Pool Check!!! ");
                System.out.println(SERVER_STATE);

                BufferedWriter LOG_FILE = new BufferedWriter(new FileWriter("c:\\infapp\\mw.log", true));
                String SMS_MESSAGE = String.format("%s%s:%s_Pool=%s",time2, SERVER_NAME, NAME, NUM_AVAILABLE);
                LOG_FILE.write(SMS_MESSAGE);
                LOG_FILE.newLine();
                LOG_FILE.close();
            }
            System.out.println(PRE_INFO + "CurrCapacityHighCount:         " + connection.getAttribute(appRT[x],"CurrCapacityHighCount"));
            System.out.println(PRE_INFO + "LeakedConnectionCount:         " + connection.getAttribute(appRT[x],"LeakedConnectionCount"));
        }
    }


    public void getInstanceHealthCheck(ObjectName on) throws Exception
    {
        String TIME = TimeStamp();
        String resourceValue = new String();
        String SERVER_NAME= new String();
        String NUM_AVAILABLE = new String();
        String MESSAGE = new String();

        resourceValue = connection.getAttribute(on,"Name").toString();
        SERVER_NAME = resourceValue;
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME, SERVER_NAME, "Instance : ", resourceValue);
        System.out.println(MESSAGE);
        monitorResultJson.put("InstanceName", resourceValue);

        resourceValue = connection.getAttribute(on,"State").toString();
        MESSAGE = String.format("%s [NOTICE-%s] %-22s  %-15s", TIME,  SERVER_NAME, "State : ", resourceValue);
        System.out.println(MESSAGE);
        monitorResultJson.put("State", resourceValue);

    }

    public void closeCollector() throws Exception {
        connector.close();
    }

    public static void main(String[] args) throws Exception {
        String hostname = "localhost";
        String portString = "13330";
        String username = "weblogic";
        String password = "forget78";


        for(int count=0 ; count < 960; count++)
        {
            try {
                wlsMonitor s = new wlsMonitor();
                s.initConnection(hostname, portString, username, password);
                int length = (int) s.serverRT.length;
                for (int i = 0; i < length; i++)
                {
                    LoadServer();
                    for (int j = 0; j < monitoringInstanceCount; j++) {
                        String name = (String) connection.getAttribute(s.serverRT[i], "Name");

                        if(servers[j].equals(name)) {
                            s.getInstanceHealthCheck(s.serverRT[i]);
                            s.getThreadHealthCheck(s.serverRT[i]);
                            s.getHeapMemoryHealthCheck(s.serverRT[i]);
                            s.getDBPoolHealthCheck(s.serverRT[i]);
                        }
                    }
                }
                String d1 = (s.monitorResultJson).toString();
                System.out.println(d1);

                Thread.sleep(180000);
                connector.close();
            } catch (Exception e) {
                Thread.sleep(180000);
            }
        }
    }
}

