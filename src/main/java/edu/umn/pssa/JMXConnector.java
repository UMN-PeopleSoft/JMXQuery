package edu.umn.pssa.jmxquery;

import java.util.Hashtable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.InvalidKeyException;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.JMRuntimeException;

/**
 * Connection class with utility functions for querying the JVM
 * JMX interface for values
 *
 * @author David Gildeh (www.outlyer.com)
 */
public class JMXConnector {

    private javax.management.remote.JMXConnector connector;
    private MBeanServerConnection connection;

    /**
     * Connect to the Java VM JMX
     *
     * @param url       JMX Connection URL
     * @param username  JMX Connection username, null if none
     * @param password  JMX Connection password, null if none
     *
     * @throws IOException
     */
    public JMXConnector(String url, String username, String password) throws IOException {
        connect(url, username, password);
    }

    /**
     * Connect to the Java VM JMX
     *
     * @param url       JMX Connection URL
     * @param username  JMX Connection username, null if none
     * @param password  JMX Connection password, null if none
     *
     * @throws IOException
     */
    private void connect(String url, String username, String password) throws IOException {

        if (url == null) {
            throw new IOException("Cannot connect to null URL. If connecting via -proc option, check the JVM process name is correct or running.");
        }

        JMXServiceURL jmxUrl = new JMXServiceURL(url);
        String WLpassword = password;
        if (username != null) {
            Hashtable env = new Hashtable();
            if (WLpassword.equals("ENV")) {
              WLpassword = System.getenv("JMX_PASSWORD");
            }
            if (url.indexOf("weblogic") > 0) {
               // Weblogic specific formatting
               env.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
               env.put(javax.naming.Context.SECURITY_PRINCIPAL, username);
               env.put(javax.naming.Context.SECURITY_CREDENTIALS, WLpassword);
            } else {
               // Tuxedo RMIs use different env variable to store/read credentials
               String credentials[] = { username, password };
               env.put("jmx.remote.credentials", ((Object) (credentials)));
            }
            connector = JMXConnectorFactory.connect(jmxUrl, env);
        } else {
            connector = JMXConnectorFactory.connect(jmxUrl);
        }

        connection = connector.getMBeanServerConnection();
    }

    /**
     * Disconnect JMX Connection
     *
     * @throws IOException
     */
    public void disconnect() throws IOException {
        if (connector != null) {
            connector.close();
            connector = null;
        }
    }

    /**
     * Fetches a list of metrics and their values in one go
     *
     * @param metricsList   List of JMXMetrics to fetch
     * @return              A list of all the MBean metrics found from the query
     * @throws java.io.IOException
     * @throws javax.management.MalformedObjectNameException
     * @throws javax.management.InstanceNotFoundException
     * @throws javax.management.IntrospectionException
     * @throws javax.management.ReflectionException
     */
    public ArrayList<JMXMetric> getMetrics(ArrayList<JMXMetric> metricsList) throws IOException,
            MalformedObjectNameException, InstanceNotFoundException, IntrospectionException, ReflectionException {

        ArrayList<JMXMetric> newMetricList = new ArrayList<JMXMetric>();
        for (JMXMetric metric : metricsList) {
            ArrayList<JMXMetric> fetchedMetrics = getMetrics(metric);
            newMetricList.addAll(fetchedMetrics);
        }
        return newMetricList;
    }

    /**
     * Main function to query and get metrics from JMX
     *
     * @param metricQuery       Metric query to filter on, use *:* to list everything
     * @return                  A list of all the MBean metrics found from the query
     * @throws java.io.IOException
     * @throws javax.management.MalformedObjectNameException
     * @throws javax.management.InstanceNotFoundException
     * @throws javax.management.IntrospectionException
     * @throws javax.management.ReflectionException
     */
    private ArrayList<JMXMetric> getMetrics(JMXMetric metricQuery) throws IOException,
            MalformedObjectNameException, InstanceNotFoundException, IntrospectionException, ReflectionException, JMRuntimeException  {

        ArrayList<JMXMetric> metrics = new ArrayList<JMXMetric>();

        MBeanInfo info = null;
        JMXMetric attributeMetric = null;

        // Get list of MBeans from MBean Query
        Set<ObjectInstance> instances = connection.queryMBeans(new ObjectName(metricQuery.getmBeanName()), null);
        Iterator<ObjectInstance> iterator = instances.iterator();

        // Iterate through results
        while (iterator.hasNext()) {

            ObjectInstance instance = iterator.next();

            try {

                // Get list of attributes for MBean
                info = connection.getMBeanInfo(new ObjectName(instance.getObjectName().toString()));
                MBeanAttributeInfo[] attributes = info.getAttributes();
                for (MBeanAttributeInfo attribute : attributes) {

                    attributeMetric= new JMXMetric(instance.getObjectName().toString(),
                                                    attribute.getName(),
                                                    null);
                    attributeMetric.setmetricName(metricQuery.getmetricName());
                    attributeMetric.setmetricLabels(metricQuery.getmetricLabels());

                    // If attribute given in query, only return those attributes
                    if ((metricQuery.getAttribute() != null) &&
                            (! metricQuery.getAttribute().equals("*"))) {

                        if (attribute.getName().equals(metricQuery.getAttribute())) {
                            // Set attribute type and get the metric(s)
                            attributeMetric.setAttributeType(attribute.getType());
                            attributeMetric.setAttribute(attribute.getName());
                            metrics.addAll(getAttributes(attributeMetric));
                        }
                    } else {

                        // Get all attributes for MBean Query
                        attributeMetric.setAttributeType(attribute.getType());
                        attributeMetric.setAttribute(attribute.getName());
                        metrics.addAll(getAttributes(attributeMetric));
                    }
                }
            } catch (NullPointerException e) {
                attributeMetric.setAttributeType(null);
                attributeMetric.setValue(null);
                metrics.add(attributeMetric);
            } catch (JMRuntimeException e) {
                // PeopleSoft App servers seems to have faults MBeans, gracefully handle missing MBeanInfo
                attributeMetric= new JMXMetric(metricQuery.getmBeanName().toString(),
                                                    metricQuery.getAttribute(),
                                                    null);
                attributeMetric.setmetricName(metricQuery.getmetricName());
                attributeMetric.setmetricLabels(metricQuery.getmetricLabels());
                attributeMetric.setAttributeType(null);
                attributeMetric.setValue(null);
                metrics.add(attributeMetric);
            }
        }

        return metrics;
    }

    /**
     * Expand an attribute to get all keys and values for it
     *
     * @param attribute     The attribute to expand
     * @return              A list of all the attribute keys/values
     */
    private ArrayList<JMXMetric> getAttributes(JMXMetric attribute) {
        return getAttributes(attribute, null);
    }

    /**
     * Recursive function to expand Attributes and get any values for them
     *
     * @param attribute     The top attribute to expand values for
     * @param value         Null if calling, used to recursively get values
     * @return              A list of all the attributes and values for the attribute
     */
    private ArrayList<JMXMetric> getAttributes(JMXMetric attribute, Object value) {

        ArrayList<JMXMetric> attributes = new ArrayList<JMXMetric>();

        if (value == null) {
            // First time running so get value from JMX connection
            try {
               value = connection.getAttribute(new ObjectName(attribute.getmBeanName()), attribute.getAttribute());
            } catch(Exception e) {
                // Do nothing - these are thrown if value is UnAvailable
            }
        }

        if (value instanceof CompositeData) {
            CompositeData cData = (CompositeData) value;
            // If attribute has key specified, only get that otherwise get all keys
            if (attribute.getAttributeKey() != null) {
                try {
                    JMXMetric foundKey = new JMXMetric(attribute.getmBeanName(),
                                                attribute.getAttribute(),
                                                attribute.getAttributeKey());
                    foundKey.setAttributeType(cData.get(attribute.getAttributeKey()));
                    foundKey.setmetricName(attribute.getmetricName());
                    foundKey.setmetricLabels(attribute.getmetricLabels());
                    attributes.addAll(getAttributes(foundKey, cData.get(attribute.getAttributeKey())));
                } catch (InvalidKeyException e) {
                    // Key doesn't exist so don't add to list
                }
            } else {
                // List all the attribute keys
                Set<String> keys = cData.getCompositeType().keySet();
                for (String key : keys) {
                    JMXMetric foundKey = new JMXMetric(attribute.getmBeanName(),
                                             attribute.getAttribute(), key);
                    foundKey.setAttributeType(cData.get(key));
                    foundKey.setmetricName(attribute.getmetricName());
                    foundKey.setmetricLabels(attribute.getmetricLabels());
                    attributes.addAll(getAttributes(foundKey, cData.get(key)));
                }
            }
        } else if (value instanceof TabularDataSupport) {
            // Ignore getting values for these types
            attribute.setAttributeType(value);
            attributes.add(attribute);
        } else {
            attribute.setAttributeType(value);
            attribute.setValue(value);
            attributes.add(attribute);
        }

        return attributes;
    }
}
