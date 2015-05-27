package org.exoplatform.chat.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.chat.utils.PropertyManager;

public class MigrateService implements Startable {

  private static final Logger LOG = LoggerFactory.getLogger(MigrateService.class);

  public MigrateService() {}

  /**
   * {@inheritDoc}
   */
  @Override
  public void start() {
    // Collect database info
    String hostname = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVER_HOST);
    String port = PropertyManager.getProperty(PropertyManager.PROPERTY_SERVER_PORT);
    String dbName = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_NAME);
    String isAuth = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_AUTHENTICATION);
    String username = "", password = "";
    if (Boolean.parseBoolean(isAuth)) {
      username = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_USER);
      password = PropertyManager.getProperty(PropertyManager.PROPERTY_DB_PASSWORD);
    }

    if (StringUtils.isEmpty(dbName)) {
      LOG.error("Database name is required. Set it in the variable 'dbName' in chat.properties");
      return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("mongo --quiet ");
    if (!StringUtils.isEmpty(hostname)) {
      sb.append(hostname);
      if (!StringUtils.isEmpty(port)) {
        sb.append(":")
          .append(port);
      }
      sb.append("/");
    }

    sb.append(dbName)
      .append(" ");

    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
      sb.append("-u ")
        .append(username)
        .append(" -p ")
        .append(password)
        .append(" ");
    }

    String migrationScriptPath = System.getProperty("catalina.base")+"/migration-chat-addon.js";
    String command = sb.append(migrationScriptPath).toString();
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(command);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = "";
      while ((line = reader.readLine())!= null) {
        output.append(line + "\n");
      }
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    LOG.info("====== Migration process output ======");
    LOG.info(output.toString());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stop() {
    // do nothing
  }
}