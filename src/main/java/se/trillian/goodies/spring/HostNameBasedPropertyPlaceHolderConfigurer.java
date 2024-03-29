/*
 * Copyright (c) 2004-2008, Trillian AB. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.trillian.goodies.spring;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * <p>
 * Spring {@link PropertyPlaceholderConfigurer} which reads host name specific
 * properties files. The names of the files will be derived from the location(s)
 * set using {@link #setLocations(Resource[])}. E.g. if a single resource,
 * <code>file:/some/path/jdbc.props</code>, has been set the following resources
 * will be used in the listed order:
 * <ol>
 *      <li>file:/some/path/jdbc-defaults.props</li>
 *      <li>file:/some/path/jdbc-defaults-<i>&lt;hostname&gt;</i>.props</li>
 *      <li>file:/some/path/jdbc.props</li>
 *      <li>file:/some/path/jdbc-<i>&lt;hostname&gt;</i>.props</li>
 * </ol>
 * </p>
 * <p>
 * The <code>hostNameFilters</code> property can be used to transform the host 
 * name a number of times before the resource locations are determined. The 
 * format of a filter is <code>regexp=>replacement</code>. E.g. if 
 * the host name is <code>test-server1</code> and <code>hostNameFilters</code> 
 * has been set to <code>["^([a-zA-Z]+).*=>$1", "^([a-zA-Z]+)-([a-zA-Z]+).*=>$1-$2"]</code> 
 * the following resources will be loaded in the following order:
 * <ol>
 *      <li>file:/some/path/jdbc-defaults.props</li>
 *      <li>file:/some/path/jdbc-defaults-test.props</li>
 *      <li>file:/some/path/jdbc-defaults-test-server.props</li>
 *      <li>file:/some/path/jdbc-defaults-test-server1.props</li>
 *      <li>file:/some/path/jdbc.props</li>
 *      <li>file:/some/path/jdbc-test.props</li>
 *      <li>file:/some/path/jdbc-test-server.props</li>
 *      <li>file:/some/path/jdbc-test-server1.props</li>
 * </ol>
 * </p>
 * <p>
 * If multiple locations have been set they will be expanded in the same manner
 * and loaded in the order they come in the array of locations passed to
 * {@link #setLocations(Resource[])}.
 * </p>
 * <p>
 * If the same property occurs in more than one of the files the one loaded 
 * last will be used. If any of the files listed above is missing it will be 
 * skipped (unless {@link #setIgnoreResourceNotFound(boolean)} has been
 * set to <code>false</code>).
 * </p>
 * <p>
 * The default location is <code>classpath:/spring/spring.properties</code>.
 * </p>
 * 
 * @author Niklas Therning
 * @version $Id$
 */
public class HostNameBasedPropertyPlaceHolderConfigurer extends
                PropertyPlaceholderConfigurer {

    private static final Logger log = LoggerFactory.getLogger(HostNameBasedPropertyPlaceHolderConfigurer.class);
    
    private List<Filter> hostNameFilters = new ArrayList<Filter>();
    private Resource[] locations;
    
    public HostNameBasedPropertyPlaceHolderConfigurer() {
        setLocation(new ClassPathResource("/spring/spring.properties"));
        setIgnoreResourceNotFound(true);
    }
    
    public void setHostNameFilters(List<String> filters) {
        this.hostNameFilters.clear();
        for (String s : filters) {
            String[] parts = s.split("=>");
            if (parts.length != 2) {
                throw new IllegalArgumentException("=> not found in string '" + s + "'");
            }
            this.hostNameFilters.add(new Filter(parts[0], parts[1]));
        }
    }
    
    @Override
    public void setLocation(Resource location) {
        this.setLocations(new Resource[] {location});
    }
    
    @Override
    protected Properties mergeProperties() throws IOException {
        Properties properties = super.mergeProperties();
        properties.put("se.trillian.goodies.hostname", getHostName());
        properties.put("se.trillian.goodies.hostname.full", getFullHostName());
        return properties;
    }
    
    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory) throws BeansException {

        String hostName = getHostName();
        try {
            
            List<Resource> tempLocations = new ArrayList<Resource>();
            for (Resource res : locations) {
                String basename = res.getFilename();
                String extension = "";
                int index = basename.lastIndexOf('.');
                if (index != -1) {
                    extension = basename.substring(index + 1);
                    basename = basename.substring(0, index);
                }
                tempLocations.add(res.createRelative(basename + "-defaults." + extension));
                for (Filter f : hostNameFilters) {
                    String filteredHostName = hostName.replaceAll(f.getPattern(), f.getReplacement());
                    if (!filteredHostName.equals(hostName)) {
                        tempLocations.add(res.createRelative(basename + "-defaults-" + filteredHostName +  "." + extension));
                    }
                }
                tempLocations.add(res.createRelative(basename + "-defaults-" + hostName +  "." + extension));
                tempLocations.add(res.createRelative(basename + "." + extension));
                for (Filter f : hostNameFilters) {
                    String filteredHostName = hostName.replaceAll(f.getPattern(), f.getReplacement());
                    if (!filteredHostName.equals(hostName)) {
                        tempLocations.add(res.createRelative(basename + "-" + filteredHostName +  "." + extension));
                    }
                }
                tempLocations.add(res.createRelative(basename + "-" + hostName + "." + extension));
            }
            
            List<Resource> newLocations = new ArrayList<Resource>();
            for (Resource r : tempLocations) {
                // Make sure we add a location only once.
                if (!newLocations.contains(r)) {
                    newLocations.add(r);
                }
            }
            super.setLocations(newLocations.toArray(new Resource[0]));
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }        
        
        super.postProcessBeanFactory(beanFactory);
    }
    
    @Override
    public void setLocations(Resource[] locations) {
        this.locations = locations;
    }
    
    protected String getFullHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
            log.warn("Could not determine the name for the current host. Using 'localhost'.");
        }
        return "localhost";
    }
    
    protected String getHostName() {
        String hostName = getFullHostName();
        int dotIndex = hostName.indexOf('.');
        if (dotIndex != -1) {
            hostName = hostName.substring(0, dotIndex);
        }
        return hostName;
    }
    
    public static class Filter {
        private final String pattern;
        private final String replacement;
        public Filter(String pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
        public String getPattern() {
            return pattern;
        }
        public String getReplacement() {
            return replacement;
        }
    }
}
