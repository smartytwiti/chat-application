package org.exoplatform.chat.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.exoplatform.chat.services.MigrateService;
 
public class MigrateServiceListener implements ServletContextListener{

  private static MigrateService migrateService;

  @Override
  public void contextDestroyed(ServletContextEvent arg0) {
    if (migrateService != null) {
      migrateService.stop();
    }
  }
 
  //Run this before web application is started
  @Override
  public void contextInitialized(ServletContextEvent arg0) {
    migrateService = new MigrateService();
    migrateService.start();
  }
}