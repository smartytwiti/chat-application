package org.exoplatform.chat.portlet.chat;


import juzu.SessionScoped;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.chat.bean.File;
import org.exoplatform.chat.utils.ChatUtils;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.http.HttpServletRequest;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.logging.Logger;

@Named("documentsData")
@SessionScoped
public class DocumentsData {

  private static final Logger LOG = Logger.getLogger(DocumentsData.class.getName());

  RepositoryService repositoryService_;

  NodeHierarchyCreator nodeHierarchyCreator_;

  SessionProviderService sessionProviderService_;

  SpaceService spaceService_;

  ListenerService listenerService_;

  public static String FILE_CREATED_ACTIVITY         = "ActivityNotify.event.FileCreated";

  public static final String TYPE_DOCUMENT="Documents";

  @Inject
  public DocumentsData(RepositoryService repositoryService, SessionProviderService sessionProviderService, NodeHierarchyCreator nodeHierarchyCreator, SpaceService spaceService, ListenerService listenerService)
  {
    repositoryService_ = repositoryService;
    nodeHierarchyCreator_= nodeHierarchyCreator;
    sessionProviderService_ = sessionProviderService;
    spaceService_ = spaceService;
    listenerService_ = listenerService;
  }

  public SessionProvider getUserSessionProvider() {
    SessionProvider sessionProvider = sessionProviderService_.getSessionProvider(null);
    return sessionProvider;
  }


  protected File getNode(String id)
  {

    SessionProvider sessionProvider = getUserSessionProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node node = getNodeById(id, session);

      File file = getFileFromNode(node);

      return file;
    }
    catch (Exception e)
    {
      LOG.warning("JCR::\n" + e.getMessage());
    }
    return null;
  }


  private File getFileFromNode(Node node) throws Exception {
    File file = new File();
    //set name
    file.setName(node.getName());
    if (node.hasProperty("exo:title")) {
      file.setTitle(Text.unescapeIllegalJcrChars(node.getProperty("exo:title").getString()));
    }

    //set uuid
    if (node.isNodeType("mix:referenceable")) file.setUuid(node.getUUID());
    //hasMeta?

    // set created date
    Calendar date = node.getProperty("exo:dateModified").getDate();
    file.setCreatedDate(date);
    //set file size
    Long size = new Long(0);
    if (node.hasNode("jcr:content"))
    {
      Node contentNode = node.getNode("jcr:content");
      size = contentNode.getProperty("jcr:data").getLength();
    }
    file.setSizeLabel(calculateFileSize(size));
    file.setSize(size);

    if (node.hasProperty("exo:lastModifier")) {
      String owner = node.getProperty("exo:lastModifier").getString();
      if ("__system".equals(owner)) owner="System";
      file.setOwner(owner);
    }

    // set path
    file.setPath(Text.escapePath(node.getPath()));
    // set public url
    HttpServletRequest request = Util.getPortalRequestContext().getRequest();
    String baseURI = request.getScheme() + "://" + request.getServerName() + ":"
            + String.format("%s", request.getServerPort());

    String url = baseURI+ "/documents/file/" +Util.getPortalRequestContext().getRemoteUser()+"/"+file.getUuid()+"/"+Text.escape(file.getName());
    file.setPublicUrl(url);

    return file;
  }


  private Node getNodeById(String id, Session session) throws Exception
  {
    Node node = null;
    if (!id.contains("/"))
    {
      node = session.getNodeByUUID(id);
    }
    else
    {
      Node rootNode = session.getRootNode();
      String path = (id.startsWith("/"))?id.substring(1):id;
      node = rootNode.getNode(path);
    }

    return node;
  }

  protected void setPermission(String id, String targetUser)
  {
    if (StringUtils.isEmpty(targetUser)) {
      LOG.warning("No target User to set permission for " + id);
      return;
    }
    SessionProvider sessionProvider = getUserSessionProvider();
    String uuid = null;
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());
      ExtendedNode node = (ExtendedNode)getNodeById(id, session);
      if(node.canAddMixin("exo:privilegeable"))  {
        node.addMixin("exo:privilegeable");
        String[] users = targetUser.split(",");
        for (String user:users)
        {
          node.setPermission(user, new String[]{ PermissionType.READ});
        }

        node.save();
      }


    }
    catch (Exception e)
    {
      LOG.warning(e.getMessage());
    }

  }

  protected String storeFile(FileItem item, String name, boolean isPrivateContext) {
    return storeFile(item, name, null, isPrivateContext);
  }

  protected String storeFile(FileItem item, String encodedFileName, String name, boolean isPrivateContext) {
    String filename;
    if (encodedFileName != null) {
      filename = encodedFileName;
    } else {
      filename = item.getName();
    }
    try {
      filename = URLDecoder.decode(filename,"utf-8");
    } catch (UnsupportedEncodingException e1) {
      // Do nothing because there is nothing to process here
    }
    filename = Utils.cleanName(filename);
    String filenameExt = "";
    String filenameBase = filename;
    if (filename.lastIndexOf(".") > -1) {
      filenameExt = filename.substring(filename.lastIndexOf("."));
      filenameBase = filename.substring(0, filename.lastIndexOf("."));
    }

    String title = Text.escapeIllegalJcrChars(filename);
    String cleanedFilenameBase = ChatUtils.cleanString(filenameBase);
    String cleanedFilenameExt = ChatUtils.cleanString(filenameExt);
    String cleanedFilename = cleanedFilenameBase.concat(cleanedFilenameExt);



    SessionProvider sessionProvider = getUserSessionProvider();
    String uuid = null;
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node homeNode;

      if (isPrivateContext)
      {
        Node userNode = nodeHierarchyCreator_.getUserNode(sessionProvider, name);
        homeNode = userNode.getNode("Private");
      }
      else
      {
        Node rootNode = session.getRootNode();
        homeNode = rootNode.getNode(getSpacePath(name));
      }

      Node docNode = homeNode.getNode("Documents");
      Node fileNode = null;
      int cpt = 1;
      
      boolean fileExist = false;
      do {
        try {
          while (docNode.hasNode(cleanedFilename))
          {
            cleanedFilename = cleanedFilenameBase.concat("-").concat(String.valueOf(cpt)).concat(cleanedFilenameExt);
            cpt++;
          }
    
          fileNode = docNode.addNode(cleanedFilename, "nt:file");
          // Change title
          if (!fileNode.hasProperty("exo:title")) {
            fileNode.addMixin("exo:rss-enable");
          }
          fileNode.setProperty("exo:title", title);
          Node jcrContent = fileNode.addNode("jcr:content", "nt:resource");
          jcrContent.setProperty("jcr:data", item.getInputStream());
          jcrContent.setProperty("jcr:lastModified", Calendar.getInstance());
          jcrContent.setProperty("jcr:encoding", "UTF-8");

          if (filename.toLowerCase().endsWith(".jpg"))
            jcrContent.setProperty("jcr:mimeType", "image/jpeg");
          else if (filename.toLowerCase().endsWith(".png"))
            jcrContent.setProperty("jcr:mimeType", "image/png");
          else if (filename.toLowerCase().endsWith(".pdf"))
            jcrContent.setProperty("jcr:mimeType", "application/pdf");
          else if (filename.toLowerCase().endsWith(".doc"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.ms-word");
          else if (filename.toLowerCase().endsWith(".xls"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.ms-excel");
          else if (filename.toLowerCase().endsWith(".ppt"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.ms-powerpoint");
          else if (filename.toLowerCase().endsWith(".docx"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
          else if (filename.toLowerCase().endsWith(".xlsx"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
          else if (filename.toLowerCase().endsWith(".pptx"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
          else if (filename.toLowerCase().endsWith(".odp"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.oasis.opendocument.presentation");
          else if (filename.toLowerCase().endsWith(".odt"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.oasis.opendocument.text");
          else if (filename.toLowerCase().endsWith(".ods"))
            jcrContent.setProperty("jcr:mimeType", "application/vnd.oasis.opendocument.spreadsheet");
          else if (filename.toLowerCase().endsWith(".zip"))
            jcrContent.setProperty("jcr:mimeType", "application/zip");
          else
            jcrContent.setProperty("jcr:mimeType", "application/octet-stream");
          session.save();
          fileExist = false;
        } catch (ItemExistsException e) {
          fileExist = true;
          docNode.refresh(false);
          cpt++;
          cleanedFilename = cleanedFilenameBase.concat("-").concat(String.valueOf(cpt)).concat(".").concat(cleanedFilenameExt);
        }
      } while (fileExist);
      uuid = fileNode.getUUID();

      // Broadcast an activity when uploading file in a space conversation
      if (!isPrivateContext) {
        listenerService_.broadcast(FILE_CREATED_ACTIVITY, null, fileNode);
      }

    }
    catch (Exception e)
    {
      LOG.warning("JCR::" + e.getMessage());
    }

    return uuid;
  }

  private String getSpacePath(String spaceDisplayname)
  {
    Space spacet = spaceService_.getSpaceByDisplayName(spaceDisplayname);

    if (spacet == null) {
      LOG.warning("Cannot get the space of " + spaceDisplayname + ". Return null.");
      return null;
    }

    return "Groups".concat(spacet.getGroupId());
  }

  public static String calculateFileSize(long fileLengthLong) {
    int fileLengthDigitCount = Long.toString(fileLengthLong).length();
    double fileSizeKB = 0.0;
    String howBig = "";
    if (fileLengthDigitCount < 4) {
      fileSizeKB = fileLengthLong;
      howBig = "Byte(s)";
    } else if (fileLengthDigitCount >= 4 && fileLengthDigitCount <= 6) {
      fileSizeKB = (new Double(fileLengthLong) / 1024.0);
      howBig = "KB";
    } else if (fileLengthDigitCount >= 7 && fileLengthDigitCount <= 9) {
      fileSizeKB = (new Double(fileLengthLong) / (1024.0 * 1024.0));
      howBig = "MB";
    } else if (fileLengthDigitCount > 9) {
      fileSizeKB = (new Double(fileLengthLong) / (1024.0 * 1024.0 * 1024.0));
      howBig = "GB";
    }
    String finalResult = roundTwoDecimals(fileSizeKB);
    return finalResult + " " + howBig;
  }

  private static String roundTwoDecimals(double d) {
    DecimalFormat twoDForm = new DecimalFormat("#.##");
    return twoDForm.format(d);
  }
}
