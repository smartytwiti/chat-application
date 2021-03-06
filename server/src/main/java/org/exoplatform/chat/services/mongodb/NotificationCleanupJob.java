package org.exoplatform.chat.services.mongodb;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.logging.Logger;

public class NotificationCleanupJob implements Job
{
  private static final Logger LOG = Logger.getLogger("NotificationCleanupJob");
  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    LOG.info("Job started");
    NotificationServiceImpl.cleanupNotifications();
    LOG.info("Job finished");
  }
}
