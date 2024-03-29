/*
  Part of OpenSMUS Source Code.
  OpenSMUS is licensed under a MIT License, compatible with both
  open source (GPL or not) and commercial development.

  Copyright (c) 2001-2008 Mauricio Piacentini <mauricio@tabuleiro.com>

  Permission is hereby granted, free of charge, to any person
  obtaining a copy of this software and associated documentation
  files (the "Software"), to deal in the Software without
  restriction, including without limitation the rights to use,
  copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the
  Software is furnished to do so, subject to the following
  conditions:

  The above copyright notice and this permission notice shall be
  included in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
  OTHER DEALINGS IN THE SOFTWARE.
*/

package net.sf.opensmus;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/////////////////////////////////////////////////////////////
public class MUSMovie implements ServerMovie {

  public final ConcurrentHashMap<String, MUSGroup> groups = new ConcurrentHashMap<>();
  public final ConcurrentHashMap<String, ServerUser> users = new ConcurrentHashMap<>();
  public final ConcurrentHashMap<String, Integer> groupSizeLimit = new ConcurrentHashMap<>();
  public final int maxConnections;
  public final Vector<ServerSideScript> scripts = new Vector<>(1);
  public final MUSGroup allUsers;
  protected final Vector<String> disabledGroups = new Vector<>();
  protected final Vector<String> notifyDisconnect = new Vector<>();
  private final MUSServer server;
  private final int messagingLevel;
  private final Hashtable<String, Integer> userLevelCache = new Hashtable<>();
  public final String name;
  public final MUSMovieProperties properties;
  public boolean enabled = true;
  public boolean persists = false;
  private int randomInt = 0;

  /////////////////////////////////////////////////////////////
  public MUSMovie(MUSServer srv, String initname) {

    server = srv;
    name = initname;
    // Get movie specific propertis
    properties = new MUSMovieProperties(name, server.m_props.m_props);

    // Get list of groups to notify about disconnections
    String[] notifygroups = properties.getStringListProperty("NotifyDisconnect");
    for (String notifygroup : notifygroups) {
      if (!notifygroup.equalsIgnoreCase("default")) {
        if (notifygroup.startsWith("@"))
          notifyDisconnect.addElement(notifygroup);
      }
    }
    // Get list of group size limits
    String[] grouplimits = properties.getStringListProperty("GroupSizeLimits");
    for (String gl : grouplimits) {
      if (!gl.equalsIgnoreCase("default")) {
        if (gl.startsWith("@")) {
          String grpname = MUSMovieProperties.parseGroupSizeName(gl);
          Integer grplimit = MUSMovieProperties.parseGroupSizeLimit(gl);
          groupSizeLimit.put(grpname.toUpperCase(), grplimit);
        }
      }
    }

    maxConnections = properties.getIntProperty("ConnectionLimit");
    messagingLevel = properties.getIntProperty("MessagingUserLevel");

    buildUserlevelCache();

    // When a movie is created add it to the server list of movies
    server.addMovie(this);
    MUSLog.Log("Movie created:" + name, MUSLog.kMov);
    allUsers = new MUSGroup(this, "@AllUsers");

    // Finally create server side script processor
    createAllServerSideScripts();

  }

  // Copies all strings in a LList to a new LList, or converts a string to a single-item LList
  // Used to parse arguments in system. commands where the argument can be either a list or a string.
  public static void GetStringListFromContents(LList list, LValue cont) {

    if (cont.getType() == LValue.vt_List) {
      // More than one string or element
      LList grinfo = (LList) cont;
      for (int e = 0; e < grinfo.m_list.size(); e++)
        GetStringListFromContents(list, grinfo.getElementAt(e));
    } else if (cont.getType() == LValue.vt_String) {
      list.addElement(cont);
    }/* else {
      // No valid string or list specified
      // Do nothing and ignore this element
    }*/

  }

  /////////////////////////////////////////////////////////////

  //    public synchronized void addGroup(MUSGroup oneGroup) {

  public static void GetGroupListFromContents(LList list, LValue cont) throws MUSErrorCode {

    if (cont.getType() == LValue.vt_List) {
      // More than one string or element
      LList grinfo = (LList) cont;
      for (int e = 0; e < grinfo.m_list.size(); e++)
        GetGroupListFromContents(list, grinfo.getElementAt(e));
    } else if (cont.getType() == LValue.vt_String) {
      list.addElement(cont);
    } else {
      // No valid string or list specified
      // Throw BadParameter error
      throw new MUSErrorCode(MUSErrorCode.BadParameter);
    }

  }
  /////////////////////////////////////////////////////////////

  public MUSServer getServer() {
    return server;
  }

  public void addGroup(MUSGroup oneGroup) {

    String gkey = oneGroup.m_name.toUpperCase();
    if (groups.putIfAbsent(gkey, oneGroup) == null) {

      // Tell group if there is a size limit for it
      Integer limit = groupSizeLimit.get(gkey);
      if (limit != null)
        oneGroup.setuserLimit(limit);

      // Check if script is already initialized
      // Not available when AllUsersGroup is created
      if (scripts != null) {
        for (ServerSideScript script : scripts) {
          script.groupCreate(oneGroup);
        }
      }
    } else
      MUSLog.Log("Error: tried to add same group twice", MUSLog.kDeb);

  }

  private void removeGroup(MUSGroup oneGroup) {

    String gkey = oneGroup.m_name.toUpperCase();

    for (ServerSideScript script : scripts) {
      script.groupDelete(oneGroup);
    }

    groups.remove(gkey);

    MUSLog.Log("Group removed:" + oneGroup.m_name, MUSLog.kGrp);
  }

  public LValue srvcmd_getUserCount() {

    LPropList pl = new LPropList();
    // MUSGroup gr = getGroup("@AllUsers");
    pl.addElement(new LSymbol("movieID"), new LString(name));
    pl.addElement(new LSymbol("numberMembers"), allUsers.srvcmd_getAllUsersCount());

    return pl;
  }

  public MUSGroup getGroup(String gname) throws GroupNotFoundException, MUSErrorCode {

    if (!gname.startsWith("@"))
      throw new MUSErrorCode(MUSErrorCode.InvalidGroupName);

    String gkey = gname.toUpperCase();
    MUSGroup group = groups.get(gkey);
    if (group == null) {
      throw new GroupNotFoundException("Group not found");
    } else {
      return group;
    }

  }

  public void notifyDisconnection(String uname) {

    if (notifyDisconnect.isEmpty())
      return;

    MUSMessage reply = new MUSMessage();
    reply.m_errCode = 0;
    reply.m_timeStamp = server.timeStamp();
    reply.m_subject = new MUSMsgHeaderString("Disconnected");
    reply.m_senderID = new MUSMsgHeaderString("System");
    reply.m_recptID = new MUSMsgHeaderStringList();

    for (String groupname : notifyDisconnect) {
      reply.m_recptID.addElement(new MUSMsgHeaderString(groupname));
    }

    reply.m_msgContent = new LString(uname);

    // We need to send this as a valid user. So we use the script...
    // This assumes all movies get a default script!
    try {
      handleMsg(scripts.elementAt(0), reply);
    } catch (ArrayIndexOutOfBoundsException e) {
      //  This should never happen
      MUSLog.Log("No scripts inited for " + name(), MUSLog.kDeb);
    }
  }

  public ServerUser getUser(String uname) throws UserNotFoundException {

    ServerUser user = users.get(uname.toUpperCase());
    if (user == null) {
      throw new UserNotFoundException("User not found");
    } else {
      return user;
    }
  }

  public void addUser(ServerUser wuser) {

    String ukey = wuser.name().toUpperCase();
    if (users.putIfAbsent(ukey, wuser) == null) {
      // The user wasn't already in the map
      for (ServerSideScript script : scripts) {
        script.userLogOn(wuser);
      }

    } else
      MUSLog.Log("tried to add same user twice...", MUSLog.kDeb);
  }

  public void removeUser(ServerUser oneuser) {

    String ukey = oneuser.name().toUpperCase();
    if (users.remove(ukey) != null) {

      for (ServerSideScript script : scripts) {
        script.userLogOff(oneuser);
      }

      // Remove the user from all groups   TODO: Do we really need to loop through ALL groups? Why not just the user's groups?
      for (MUSGroup group : groups.values()) {
        group.removeUser(oneuser);
      }

      notifyDisconnection(oneuser.name());
    }

    MUSLog.Log("User " + oneuser.name() + " logged off from movie " + this.name() + " after " + ((server.timeStamp() - oneuser.creationTime()) / 1000) + " seconds", MUSLog.kUsr);

    if (users.isEmpty() && !persists)
      server.removeMovie(this);

  }

  public void removeAllUsers() {

    for (ServerUser mu : users.values()) {
      mu.deleteUser();
    }

    users.clear();

    // This will kill the movie
    if (users.isEmpty()) {
      MUSLog.Log("killing movie " + name, MUSLog.kMov);
      server.removeMovie(this);
    }
  }

  public void checkStructure() {

    for (ServerUser user : users.values()) {
      if (!server.userThreadAlive(user.name())) {
        MUSLog.Log("Found dead user at movie:" + name + ", user:" + user.name(), MUSLog.kDeb);
        removeUser(user);
      }
    }

    // Now groups
    for (MUSGroup group : groups.values()) {
      group.checkStructure();
    }
  }

  public boolean userThreadAlive(String uname) {
    return server.userThreadAlive(uname);
  }

  public LValue srvcmd_getGroups() {

    LPropList pl = new LPropList();
    pl.addElement(new LSymbol("movieID"), new LString(name));
    LList cl = new LList();

    for (MUSGroup group : groups.values()) {
      cl.addElement(new LString(group.m_name));
    }

    pl.addElement(new LSymbol("groups"), cl);
    return pl;
  }

  public LValue srvcmd_getGroupCount() {

    LPropList pl = new LPropList();
    pl.addElement(new LSymbol("movieID"), new LString(name));
    pl.addElement(new LSymbol("numberGroups"), new LInteger(groups.size()));
    return pl;

  }

  public LValue srvcmd_createUniqueName() {

    String gname = "@RndGroup" + randomInt;
    randomInt++;
    return new LString(gname);
  }

  public boolean isGroupAllowed(String groupname) {

    // Check the groups that have been disabled
    for (String gn : disabledGroups) {
      if (groupname.equalsIgnoreCase(gn))
        return false;
    }
    return true;
  }

  public boolean isConnectionAllowed(ServerUser user) {
    return users.size() < maxConnections;

  }

  public void disableGroup(String gname) {

    boolean inList = false;
    for (String gn : disabledGroups) {
      if (gname.equalsIgnoreCase(gn)) {
        inList = true;
        break;
      }
    }

    if (!inList)
      disabledGroups.addElement(gname);

    // Mark the group instance, if it exists
    try {
      MUSGroup mg = getGroup(gname);
      mg.m_enabled = false;
    } catch (GroupNotFoundException mnf) {
      // That's OK
    } catch (MUSErrorCode mer) {
      //
    }
  }

  // ServerMovie interface methods

  public void enableGroup(String gname) {

    for (String gn : disabledGroups) {
      if (gname.equalsIgnoreCase(gn)) {
        disabledGroups.removeElement(gn);
        break;
      }
    }

    // Mark the group instance, if it exists
    try {
      MUSGroup mg = getGroup(gname);
      mg.m_enabled = true;
    } catch (GroupNotFoundException mnf) {
      // That's OK
    } catch (MUSErrorCode mer) {
      //
    }
  }

  public void logDroppedMsg() {
    server.logDroppedMsg();
  }

  public ServerGroup getServerGroup(String groupname) throws GroupNotFoundException {
    try {
      return getGroup(groupname);
    } catch (MUSErrorCode e) {
      throw new GroupNotFoundException("Group not found");
    }
  }

  public ServerGroup getServerGroup(int groupidx) throws GroupNotFoundException {

    try {
      // @TODO: Can't this be optimized without a loop?
      Enumeration<MUSGroup> enume = groups.elements();
      int enumidx = 1;
      while (enume.hasMoreElements()) {
        MUSGroup gn = enume.nextElement();
        if (groupidx == enumidx)
          return gn;

        enumidx++;
      }
      // Throw group not found otherwise
      throw new GroupNotFoundException("Group not found");
    } catch (Exception e) {
      throw new GroupNotFoundException("Group not found");
    }
  }

  public ServerGroup createServerGroup(String groupname) throws MUSErrorCode {

    if (!groupname.startsWith("@"))
      throw new MUSErrorCode(MUSErrorCode.InvalidGroupName);

    if (groupname.equalsIgnoreCase("@AllUsers"))
      throw new MUSErrorCode(MUSErrorCode.InvalidGroupName);

    if (!isGroupAllowed(groupname))
      throw new MUSErrorCode(MUSErrorCode.ErrorJoiningGroup);

    try {
      return getGroup(groupname);
    } catch (GroupNotFoundException | MUSErrorCode gnf) {
      return new MUSGroup(this, groupname);
    }
  }

  public void deleteServerGroup(String groupname) {

    try {
      if ((persists) && groupname.equalsIgnoreCase("@AllUsers"))
        return;

      MUSGroup wgroup = getGroup(groupname);
      removeGroup(wgroup);
    } catch (Exception gnf) {
      MUSLog.Log("Unable to delete group " + groupname, MUSLog.kDeb);
    }
  }

  public int serverGroupCount() {
    return groups.size();
  }

  public int serverUserCount() {
    return users.size();
  }

  public String name() {
    return name;
  }

  public int userLevel() {
    return 20;
  }

  public void setuserLevel(int level) {

  }


  /////////// Stuff below from MUSDispatcher ///////////

  public boolean persists() {
    return persists;
  }

  public void setpersists(boolean persistflag) {
    persists = persistflag;
  }

  public void createAllServerSideScripts() {

    String[] scripts = server.m_scriptmap.getScriptName(name);
    for (String className : scripts) {
      if (!createServerSideScript(className)) {
        MUSLog.Log("Error loading server side script class '" + className + "' for movie " + name, MUSLog.kDeb);
      }
    }

    // Add a default script if necessary
    if (this.scripts.isEmpty()) this.scripts.add(new ServerSideScript());
  }

  public boolean createServerSideScript(int scriptNumber) {

    String[] scripts = server.m_scriptmap.getScriptName(name);
    if (scripts.length < scriptNumber) {
      return false;
    }
    if (!createServerSideScript(scripts[scriptNumber])) {
      MUSLog.Log("Error loading server side script class '" + scripts[scriptNumber] + "' for movie " + name, MUSLog.kDeb);
      return false;
    }
    return true;
  }

  boolean createServerSideScript(String className) {

    MUSLog.Log("Loading server side script class '" + className + "' for movie " + name, MUSLog.kDeb);

    // The standard class loader will not reload an updated class for you.
    // It will use the first copy of the class, not any updates.
    // We need to use our own class loader to dunamically refresh updated scripts.

    // It is important that the reloadable class not be on the classpath.
    // Otherwise, the class will be loaded by some parent of the new class loader rather than by the new class loader itself.
    File dir = new File("scripts");
    // Prepare a filter to filter out only directory names (in the scripts directory)

    List<URL> urlList = new ArrayList<>(); // List of all directories to be added to the classpath
    try {
      URL url = dir.toURI().toURL();
      urlList.add(url); // Add the "./scripts/" directory

      // Add all directories in the scripts directory as well
      // (So we can be neat and have each script in a subdir)
      File[] files = dir.listFiles(File::isDirectory);
      for (File f : files) {
        url = f.toURI().toURL();
        urlList.add(url);
      }
    } catch (MalformedURLException | NullPointerException e) {
      return false;
    }

    URL[] urls = urlList.toArray(new URL[0]); // Convert into array of URLs

    // Create new classloader, specifying our list of directories to use as classpath
    try(URLClassLoader loader = new URLClassLoader(urls)) {
      // Load the class that was specified
      Class<?> scriptclass = loader.loadClass(className);
      // Create a new instance of the new class
      ServerSideScript newScript = (ServerSideScript) scriptclass
              .getDeclaredConstructor()
              .newInstance();
      scripts.add(newScript);
      newScript.initScript(server, this);
      return true;
    } catch (Exception | LinkageError e) {
      return false;
    }
  }

  public boolean deleteServerSideScript(int scriptNum) {

    MUSLog.Log("Removing server side script number " + scriptNum + " in movie " + name, MUSLog.kDeb);

    if (scripts.size() >= scriptNum && scriptNum >= 0) {
      ServerSideScript script = scripts.remove(scriptNum);
      script.scriptDelete();
      // Add a default script if necessary
      if (scripts.isEmpty()) scripts.add(new ServerSideScript());
      return true;
    } else {
      MUSLog.Log("Error removing server side script class number" + scriptNum + " in movie " + name, MUSLog.kDeb);
      return false;
    }
  }

  public boolean deleteServerSideScript(String scriptName) {

    MUSLog.Log("Removing server side script '" + scriptName + "' in movie " + name, MUSLog.kDeb);

    for (Iterator<ServerSideScript> it = scripts.iterator(); it.hasNext(); ) {
      ServerSideScript script = it.next();
      String n = script.getClass().getName();
      if (n.equalsIgnoreCase(scriptName)) {
        script.scriptDelete();
        it.remove();
        // Add a default script if necessary
        if (scripts.isEmpty()) scripts.add(new ServerSideScript());
        return true;
      }
    }

    MUSLog.Log("Error removing server side script class '" + scriptName + "' in movie " + name, MUSLog.kDeb);
    return false;
  }

  public void buildUserlevelCache() {

    // To speed up lookup of userlevels required for each command
    Enumeration<Object> enume = properties.movieProps.keys();
    while (enume.hasMoreElements()) {
      String thiskey = (String) enume.nextElement();
      if (thiskey.startsWith("UserLevel.")) {
        String ckey = thiskey.substring(thiskey.indexOf(".") + 1).toUpperCase(); // Remove the "UserLevel." prefix
        int ulevel;
        try {
          ulevel = Integer.parseInt((String) properties.movieProps.get(thiskey));
        } catch (NumberFormatException e) {
          ulevel = properties.getIntProperty("DefaultUserLevel");
        }
        userLevelCache.put(ckey, ulevel);
      }
    }
  }

  public int getRequiredUserLevel(String command) {

    // Check if the userlevel is enough
    try {
      return userLevelCache.get(command.toUpperCase());
    } catch (NullPointerException nl) {
      // An invalid command, for which no userlevel exists
      return 0;
    }
  }

  public void handleMsg(ServerUser user, MUSMessage msg) {

    // user.testMessage();
    Enumeration<MUSMsgHeaderString> recipients = msg.m_recptID.elements();
    String recpt;
    while (recipients.hasMoreElements()) {
      MUSMsgHeaderString MUSrecpt = recipients.nextElement();
      recpt = MUSrecpt.toString();

      // Check if the message is addressed to this movie or another one
      if (recpt.indexOf("@", 1) != -1) {
        MUSLog.Log("message received to another movie", MUSLog.kDeb);
        int cutPoint = recpt.indexOf("@", 1);
        String tgtMovie = recpt.substring(cutPoint + 1);
        String urecpt = recpt.substring(0, cutPoint);

        // Make it into a LList in order to be compatible with the
        // return value from srvcmd_getMovies
        LList movlist = new LList();

        if (tgtMovie.equalsIgnoreCase("AllMovies"))
          movlist = (LList) server.srvcmd_getMovies();
        else
          movlist.addElement(new LString(tgtMovie));

        for (int e = 0; e < movlist.count(); e++) {
          LString movname = (LString) movlist.getElementAt(e);
          MUSMovie wmov;
          try {
            wmov = server.getMovie(movname.toString());
            if (!movname.toString().equalsIgnoreCase(name()))
              msg.m_senderID = new MUSMsgHeaderString(user.name() + "@" + name());
            wmov.handleLocalMsg(user, urecpt, msg);
          } catch (MovieNotFoundException mnf) {
            // Fail silently
          }
        }
      } else {
        // Handle message in this movie
        handleLocalMsg(user, recpt, msg);
      }
    }

  }

  public void handleLocalMsg(ServerUser user, String recpt, MUSMessage msg) {

    if (recpt.regionMatches(true, 0, "system.", 0, 7)) {  // toLowerCase().startsWith("system.")
      handleSystemMsg(user, recpt, msg);
      return;
    }

    if (user.userLevel() < messagingLevel) { // Ignore messages if user hasn't got enough privs.
      MUSLog.Log("REJECTED MSG: " + user + ": " + msg, MUSLog.kDeb);
      msg.m_msgContent = new LVoid();
      msg.m_errCode = MUSErrorCode.NotPermittedWithUserLevel;
      user.sendMessage(msg);
      return;
    }

    // User cleared to send messages. Determine if it's to a group or to a user.
    if (recpt.startsWith("@")) {
      handleGroupMsg(user, recpt, msg);
    } else {
      // Message to a specific user
      try {
        // @TODO: Try and get rid of this extra creation of a new MUSMessage object and use the original one
        // will be tricky if the original message has multiple recipients...
        ServerUser thisuser = getUser(recpt);
        MUSMessage reply = new MUSMessage(msg);
        reply.m_timeStamp = server.timeStamp();
        reply.m_recptID = new MUSMsgHeaderStringList();
        reply.m_recptID.addElement(new MUSMsgHeaderString(thisuser.name()));
        reply.m_udp = msg.m_udp;
        thisuser.sendMessage(reply);
      } catch (UserNotFoundException unf) {
        // Here we could return "no such user" errors to the sender...
      }
    }
  }

  public void handleSystemMsg(ServerUser user, String recpt, MUSMessage msg) {
    try {
      StringTokenizer st = new StringTokenizer(recpt, ".");
      if (st.countTokens() != 3) { // Recipient must be in the form "system.x.y"
        // @TODO: The original SMUS 3 allowed messages to "system.script" (only 2 tokens)
        // Bad package, return
        return;
      }

      String[] args = new String[3];
      // We know there are exactly 3 tokens, put them into an array
      args[0] = st.nextToken();
      args[1] = st.nextToken();
      args[2] = st.nextToken();

      MUSMessage reply = new MUSMessage();
      reply.m_errCode = 0;
      reply.m_timeStamp = server.timeStamp();
      reply.m_subject = new MUSMsgHeaderString(msg.m_subject.toString());
      reply.m_senderID = new MUSMsgHeaderString(recpt);
      reply.m_recptID = new MUSMsgHeaderStringList();
      reply.m_recptID.addElement(new MUSMsgHeaderString(user.name()));
      reply.m_msgContent = new LValue();
      reply.m_udp = msg.m_udp;

      // Some commands will receive the same msg as a reply, update the timestamp here
      msg.m_timeStamp = server.timeStamp();

      // Check if the userlevel is enough
      try {
        Integer reqlevel = userLevelCache.get(recpt.toUpperCase());
        if (reqlevel > user.userLevel()) {
          msg.m_msgContent = new LVoid(); // Clear contents to avoid bandwidth flooding with large messages
          msg.m_errCode = MUSErrorCode.NotPermittedWithUserLevel;
          user.sendMessage(msg); // @TODO: Bug? Recipient will be listed as "system"...
          return;
        }
      } catch (NullPointerException nl) {
        // An invalid command, for which no userlevel exists
        // if (! args[1].equalsIgnoreCase("script")) // Exclude system.script.x commands
        MUSLog.Log("Warning: no user level found for command " + recpt, MUSLog.kDeb);
      }

      // Special function to process Database messages
      if (args[1].equalsIgnoreCase("DBAdmin") ||
              args[1].equalsIgnoreCase("DBUser") ||
              args[1].equalsIgnoreCase("DBPlayer") ||
              args[1].equalsIgnoreCase("DBApplication")
      ) {
        server.m_dbConn.deliver(user, this, args, msg, reply); // handleDatabaseMsg
        return;
      }

      // Special function to process SQL messages
      if (args[1].equalsIgnoreCase("SQL")) {
        server.m_sqlConn.deliver(user, this, args, msg, reply); // handleDatabaseMsg
        return;
      }

      if (args[1].equalsIgnoreCase("server")) {
        if (args[2].equalsIgnoreCase("getVersion")) {
          reply.m_msgContent = server.srvcmd_getVersion();
        } else if (args[2].equalsIgnoreCase("getTime")) {
          reply.m_msgContent = new LString(server.timeString());
        } else if (args[2].equalsIgnoreCase("getMovieCount")) {
          reply.m_msgContent = new LInteger(server.serverMovieCount());
        } else if (args[2].equalsIgnoreCase("getUserCount")) {
          reply.m_msgContent = server.srvcmd_getUserCount();
        } else if (args[2].equalsIgnoreCase("getMovies")) {
          reply.m_msgContent = server.srvcmd_getMovies();
        }

        // OpenSMUS specific

        else if (args[2].equalsIgnoreCase("restart")) {
          new MUSKillServerTimer(server, 15000);
          reply.m_msgContent = new LString("ServerRestarted");
        } else if (args[2].equalsIgnoreCase("shutdown")) {
          new MUSShutdownServerTimer(server, 10000);
          reply.m_msgContent = new LString("ServerRestarted");
        } else if (args[2].equalsIgnoreCase("disable")) {
          server.disable();
          reply.m_msgContent = new LString("ServerDisabled");
        } else if (args[2].equalsIgnoreCase("enable")) {
          server.enable();
          reply.m_msgContent = new LString("ServerEnabled");
        } else if (args[2].equalsIgnoreCase("disconnectAll")) {
          server.disconnectAllUsers();
          reply.m_msgContent = new LString("DisconnectAll");
        } /*else if (args[2].equalsIgnoreCase("setFloodParameters")) { // Kinda kludgy at the moment
                    LValue msgcont = msg.m_msgContent;
                    if (msgcont.getType() != LValue.vt_List) {
                        // Error, we need a list
                        reply.m_errCode = MUSErrorCode.BadParameter;
                        reply.m_msgContent = new LInteger(0);
                        user.sendMessage(reply);
                        return;
                    }

                    LList params = (LList) msgcont;

                    if (params.count() < 3) {
                        // Error, we need 3 values
                        reply.m_errCode = MUSErrorCode.BadParameter;
                        reply.m_msgContent = new LInteger(0);
                        user.sendMessage(reply);
                        return;
                    }
                    // Assume the list contents are integers
                    int minTime = params.getElementAt(0).toInteger();
                    int tolerance = params.getElementAt(1).toInteger();
                    int repeat = params.getElementAt(2).toInteger();

                    ((SMUSPipelineFactory) m_server.m_ports.firstElement().bootstrap.getPipelineFactory()).setFloodParameters(minTime, tolerance, repeat);
                    reply.m_msgContent = new LString("Flood filter parameters updated to " + minTime +" "+ tolerance +" "+ repeat);
                }*/ else if (args[2].equalsIgnoreCase("sendEmail")) {
          // Command requires a property list
          LValue msgcont = msg.m_msgContent;
          if (msgcont.getType() != LValue.vt_PropList) {
            // Error, we need a proplist
            reply.m_errCode = MUSErrorCode.BadParameter;
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }

          LPropList plist = (LPropList) msgcont;
          LValue argsender;
          LValue argrecpt;
          LValue argsubject;
          LValue argsmtphost;
          LValue argdata;

          try {
            try {
              argsender = plist.getElement(new LSymbol("sender"));
              argrecpt = plist.getElement(new LSymbol("recpt"));
              argsubject = plist.getElement(new LSymbol("subject"));
              argsmtphost = plist.getElement(new LSymbol("smtphost"));
              argdata = plist.getElement(new LSymbol("data"));
            } catch (PropertyNotFoundException pnf) {
              // All properties are needed
              throw new MUSErrorCode(MUSErrorCode.BadParameter);
            }

            // Check types for arguments
            if (argsender.getType() != LValue.vt_String ||
                    argrecpt.getType() != LValue.vt_String ||
                    argsubject.getType() != LValue.vt_String ||
                    argsmtphost.getType() != LValue.vt_String ||
                    argdata.getType() != LValue.vt_List) {
              throw new MUSErrorCode(MUSErrorCode.BadParameter);
            }

            LList argdatalist = (LList) argdata;
            String[] datalist = new String[argdatalist.count()];

            for (int i = 0; i < argdatalist.count(); i++) {
              datalist[i] = argdatalist.getElementAt(i).toString();
            }

            new MUSEmail(argsender.toString(), argrecpt.toString(), argsubject.toString(), argsmtphost.toString(), datalist);
            reply.m_msgContent = new LString("EmailAccepted");
          } catch (MUSErrorCode err) {
            reply.m_errCode = err.m_errCode;
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }

        }
        user.sendMessage(reply);
      } // End server commands

      else if (args[1].equalsIgnoreCase("movie")) {
        // All movie commands take the same content list
        LList contlist = new LList();
        GetStringListFromContents(contlist, msg.m_msgContent);

        // Enable, disable and delete require at least one string
        if (args[2].equalsIgnoreCase("enable") ||
                args[2].equalsIgnoreCase("disable") ||
                args[2].equalsIgnoreCase("delete")
        ) {
          // These commands fail if no group is specified
          if (contlist.count() == 0) {
            reply.m_errCode = MUSErrorCode.BadParameter;
            // SMUS 3 replies with a content of 0
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }
        }

        if (args[2].equalsIgnoreCase("enable")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString movname = (LString) contlist.getElementAt(e);
            server.enableMovie(movname.toString());
          }
          user.sendMessage(msg); // @TODO: Bug?
          return;
        } else if (args[2].equalsIgnoreCase("disable")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString movname = (LString) contlist.getElementAt(e);
            server.disableMovie(movname.toString());
          }
          user.sendMessage(msg); // @TODO: Bug?
          return;
        } else if (args[2].equalsIgnoreCase("delete")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString movname = (LString) contlist.getElementAt(e);
            MUSMovie thismov;
            try {
              thismov = server.getMovie(movname.toString());
              thismov.removeAllUsers();
            } catch (MovieNotFoundException mnf) {
              // Not a problem
            }
          }
          user.sendMessage(msg); // @TODO: Bug?
          return;
        }

        // Remaining movie commands are addressed to the current movie if none is specified
        if (contlist.count() == 0) {
          contlist.addElement(new LString(name));
        }

        if (args[2].equalsIgnoreCase("getGroupCount")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString movname = (LString) contlist.getElementAt(e);
            MUSMovie thismov;
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            try {
              thismov = server.getMovie(movname.toString());
              onereply.m_errCode = 0;
              onereply.m_msgContent = thismov.srvcmd_getGroupCount();
            } catch (MovieNotFoundException mnf) {
              LPropList pl = new LPropList();
              onereply.m_errCode = MUSErrorCode.InvalidMovieID;
              pl.addElement(new LSymbol("movieID"), new LString(movname.toString()));
              pl.addElement(new LSymbol("numberGroups"), new LInteger(0));
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("getGroups")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString movname = (LString) contlist.getElementAt(e);
            MUSMovie thismov;
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            try {
              thismov = server.getMovie(movname.toString());
              onereply.m_errCode = 0;
              onereply.m_msgContent = thismov.srvcmd_getGroups();
            } catch (MovieNotFoundException mnf) {
              LPropList pl = new LPropList();
              onereply.m_errCode = MUSErrorCode.BadParameter;
              pl.addElement(new LSymbol("movieID"), new LString(movname.toString()));
              pl.addElement(new LSymbol("groups"), new LList());
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("getUserCount")) {
          //try {
            for (int e = 0; e < contlist.count(); e++) {
              LString movname = (LString) contlist.getElementAt(e);
              MUSMovie thismov;
              reply.m_msgContent = new LVoid();
              MUSMessage onereply = new MUSMessage(reply);
              try {
                thismov = server.getMovie(movname.toString());
                onereply.m_errCode = 0;
                onereply.m_msgContent = thismov.srvcmd_getUserCount();
              } catch (MovieNotFoundException mnf) {
                LPropList pl = new LPropList();
                onereply.m_errCode = MUSErrorCode.BadParameter;
                pl.addElement(new LSymbol("movieID"), new LString(movname.toString()));
                pl.addElement(new LSymbol("numberMembers"), new LInteger(0));
                onereply.m_msgContent = pl;
              }
              user.sendMessage(onereply);
            }
          /*} catch (GroupNotFoundException gnf) {
            // Should not happen, we are querying for the allusers group
          }*/
        }

        // OpenSMUS specific

        else if (args[2].equalsIgnoreCase("getScriptCount")) {
          for (int e = 0; e < contlist.count(); e++) {
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            String movname = contlist.getElementAt(e).toString();
            MUSMovie thismov;
            try {
              thismov = server.getMovie(movname);
              onereply.m_errCode = 0;
              onereply.m_msgContent = new LInteger(thismov.scripts.size());
            } catch (MovieNotFoundException mnf) {
              LPropList pl = new LPropList();
              onereply.m_errCode = MUSErrorCode.InvalidMovieID;
              pl.addElement(new LSymbol("movieID"), new LString(movname));
              pl.addElement(new LSymbol("numberScripts"), new LInteger(0));
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("reloadAllScripts")) {
          for (int e = 0; e < contlist.count(); e++) {
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            String movname = contlist.getElementAt(e).toString();
            MUSMovie thismov;

            try {
              thismov = server.getMovie(movname);
              onereply.m_errCode = 0;

              // Remove all scripts
              for (ServerSideScript script : scripts) {
                this.deleteServerSideScript(script.getClass().getName());
              }

              // Start up the scripts again
              thismov.createAllServerSideScripts();

            } catch (MovieNotFoundException mnf) {
              LPropList pl = new LPropList();
              onereply.m_errCode = MUSErrorCode.InvalidMovieID;
              pl.addElement(new LSymbol("movieID"), new LString(movname));
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }

        } else if (args[2].equalsIgnoreCase("reloadScript")) {
          // Always work on the current movie. The contents is a list of script class names to reload.
          for (int e = 0; e < contlist.count(); e++) {
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            onereply.m_errCode = 0;
            String scriptName = contlist.getElementAt(e).toString();
            onereply.m_msgContent = new LString(scriptName);

            this.deleteServerSideScript(scriptName);
            // Start up the script again
            if (!this.createServerSideScript(scriptName)) {
              onereply.m_errCode = MUSErrorCode.InvalidMovieID; // @TODO: Fix errorcode
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("deleteScript")) {
          // Always work on the current movie. The contents is a list of script class names to delete.
          for (int e = 0; e < contlist.count(); e++) {
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);
            onereply.m_errCode = 0;
            String scriptName = contlist.getElementAt(e).toString();
            onereply.m_msgContent = new LString(scriptName);

            if (!this.deleteServerSideScript(scriptName)) {
              // No such script found
              onereply.m_errCode = MUSErrorCode.InvalidMovieID; // @TODO: Fix errorcode
            }
            user.sendMessage(onereply);
          }
        }

      }// End movie commands

      else if (args[1].equalsIgnoreCase("group")) {
        // Special function to process group Attribute messages
        if (args[2].equalsIgnoreCase("setAttribute") ||
                args[2].equalsIgnoreCase("getAttribute") ||
                args[2].equalsIgnoreCase("getAttributeNames") ||
                args[2].equalsIgnoreCase("deleteAttribute")
        ) {
          handleGroupAttributeMsg(user, args[2], msg, reply);
          return;
        }

        // createuniquename does not require parameters
        else if (args[2].equalsIgnoreCase("createUniqueName")) {
          reply.m_msgContent = srvcmd_createUniqueName();
          user.sendMessage(reply);
          return;
        }

        // Most group commands accept a string list
        LList contlist = new LList();
        GetStringListFromContents(contlist, msg.m_msgContent);

        // Group commands fail if no group is specified
        if (contlist.count() == 0) {
          reply.m_errCode = MUSErrorCode.BadParameter;
          // SMUS 3 replies with a content of 0
          reply.m_msgContent = new LInteger(0);
          user.sendMessage(reply);
          return;
        }

        if (args[2].equalsIgnoreCase("enable")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            enableGroup(groupname.toString());
          }
          user.sendMessage(msg); // @TODO: Bug?
        } else if (args[2].equalsIgnoreCase("disable")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            disableGroup(groupname.toString());
          }
          user.sendMessage(msg); // @TODO: Bug?
        } else if (args[2].equalsIgnoreCase("delete")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            MUSGroup thisgroup;
            try {
              thisgroup = getGroup(groupname.toString());
              thisgroup.removeAllUsers();
            } catch (GroupNotFoundException | MUSErrorCode mnf) {
              // Not a problem
            }
          }
          user.sendMessage(msg); // @TODO: Bug?
        } else if (args[2].equalsIgnoreCase("getUsers")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            reply.m_errCode = 0;
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              MUSGroup thisgroup = getGroup(groupname.toString());
              onereply.m_msgContent = thisgroup.srvcmd_getUsers();
            } catch (GroupNotFoundException gnf) {
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("groupName"), new LString(groupname.toString()));
              LList ml = new LList();
              pl.addElement(new LSymbol("groupMembers"), ml);
              onereply.m_msgContent = pl;
            } catch (MUSErrorCode err) {
              onereply.m_errCode = err.m_errCode;
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("groupName"), new LString(groupname.toString()));
              LList ml = new LList();
              pl.addElement(new LSymbol("groupMembers"), ml);
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("getUserCount")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            reply.m_errCode = 0;

            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              MUSGroup thisgroup = getGroup(groupname.toString());
              onereply.m_msgContent = thisgroup.srvcmd_getUserCount();
            } catch (GroupNotFoundException gnf) {
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("groupName"), new LString(groupname.toString()));
              pl.addElement(new LSymbol("numberMembers"), new LInteger(0));
              onereply.m_msgContent = pl;
            } catch (MUSErrorCode err) {
              onereply.m_errCode = err.m_errCode;
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("groupName"), new LString(groupname.toString()));
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("join")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              onereply.m_errCode = 0;
              srvcmd_joinGroup(user, groupname.toString());
            } catch (MUSErrorCode err) {
              onereply.m_errCode = err.m_errCode;
            }
            onereply.m_msgContent = groupname;
            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("leave")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString groupname = (LString) contlist.getElementAt(e);
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              onereply.m_errCode = 0;
              srvcmd_leaveGroup(user, groupname.toString());
            } catch (MUSErrorCode err) {
              onereply.m_errCode = err.m_errCode;
            }
            onereply.m_msgContent = groupname;
            user.sendMessage(onereply);
          }
        }

      } else if (args[1].equalsIgnoreCase("user")) {
        // All user commands take the same content list
        LList contlist = new LList();
        GetStringListFromContents(contlist, msg.m_msgContent);

        if (args[2].equalsIgnoreCase("delete")) {
          // Delete fails if no user is specified

          if (contlist.count() == 0) {
            reply.m_errCode = MUSErrorCode.BadParameter;
            // SMUS 3 replies with a content of 0
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }

          for (int e = 0; e < contlist.count(); e++) {
            LString username = (LString) contlist.getElementAt(e);
            ServerUser thisuser;
            try {
              thisuser = getUser(username.toString());
              thisuser.deleteUser();
            } catch (UserNotFoundException mnf) {
              // Not a problem
            }
          }
          user.sendMessage(msg); // @TODO: Bug?
          return;
        } else if (args[2].equalsIgnoreCase("getAddress")) {
          // getAddress fails if no user is specified
          if (contlist.count() == 0) {
            reply.m_errCode = MUSErrorCode.BadParameter;
            // SMUS 3 replies with a content of 0
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }
          for (int e = 0; e < contlist.count(); e++) {
            LString username = (LString) contlist.getElementAt(e);
            reply.m_errCode = 0;
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              ServerUser thisuser = getUser(username.toString());
              LPropList ipl = new LPropList();
              ipl.addElement(new LSymbol("userID"), new LString(thisuser.name()));
              ipl.addElement(new LSymbol("ipAddress"), new LString(thisuser.ipAddress()));
              onereply.m_msgContent = ipl;
            } catch (UserNotFoundException gnf) {
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("userID"), new LString(username.toString()));
              pl.addElement(new LSymbol("ipAddress"), new LString("0.0.0.0"));
              onereply.m_msgContent = pl;
              onereply.m_errCode = MUSErrorCode.InvalidUserID;
            }
            user.sendMessage(onereply);
          }
          return;
        } else if (args[2].equalsIgnoreCase("changeMovie")) {
          // ChangeMovie fails if no user is specified
          if (contlist.count() == 0) {
            reply.m_errCode = MUSErrorCode.BadParameter;
            // SMUS 3 replies with a content of 0
            reply.m_msgContent = new LInteger(0);
            user.sendMessage(reply);
            return;
          }
          // Only the first moviename is used
          LString moviename = (LString) contlist.getElementAt(0);
          reply.m_errCode = 0;
          try {
            server.changeUserMovie(user, moviename.toString());
          } catch (MUSErrorCode err) {
            reply.m_errCode = err.m_errCode;
          }
          reply.m_msgContent = moviename;
          user.sendMessage(reply);
          return;
        }

        // Remaining user commands are addressed to the current user if none is specified
        if (contlist.count() == 0) {
          contlist.addElement(new LString(user.name()));
        }

        if (args[2].equalsIgnoreCase("getGroupCount")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString username = (LString) contlist.getElementAt(e);
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              ServerUser thisuser = getUser(username.toString());
              LPropList ipl = new LPropList();
              ipl.addElement(new LSymbol("userID"), new LString(thisuser.name()));
              ipl.addElement(new LSymbol("numberGroups"), new LInteger(thisuser.getGroupsCount()));
              onereply.m_msgContent = ipl;
            } catch (UserNotFoundException gnf) {
              LPropList pl = new LPropList();
              onereply.m_errCode = MUSErrorCode.InvalidUserID;
              pl.addElement(new LSymbol("userID"), new LString(username.toString()));
              pl.addElement(new LSymbol("numberGroups"), new LInteger(0));
              onereply.m_msgContent = pl;
            }

            user.sendMessage(onereply);
          }
        } else if (args[2].equalsIgnoreCase("getGroups")) {
          for (int e = 0; e < contlist.count(); e++) {
            LString username = (LString) contlist.getElementAt(e);
            reply.m_msgContent = new LVoid();
            MUSMessage onereply = new MUSMessage(reply);

            try {
              ServerUser thisuser = getUser(username.toString());
              LPropList gpl = new LPropList();
              gpl.addElement(new LSymbol("userID"), new LString(thisuser.name()));
              LList cl = new LList();
              Vector<String> grouplist = thisuser.getGroupNames();
              for (String groupname : grouplist) {
                cl.addElement(new LString(groupname));
              }
              gpl.addElement(new LSymbol("groups"), cl);
              onereply.m_msgContent = gpl;
            } catch (UserNotFoundException gnf) {
              onereply.m_errCode = MUSErrorCode.InvalidUserID;
              LPropList pl = new LPropList();
              pl.addElement(new LSymbol("userID"), username); // new LString(username.toString())
              LList cl = new LList();
              pl.addElement(new LSymbol("groups"), cl);
              onereply.m_msgContent = pl;
            }
            user.sendMessage(onereply);
          }
        }
      } else if (args[1].equalsIgnoreCase("script")) {
        // @TODO: Support for targeting specific scripts should be added here
        for (ServerSideScript script : scripts) {
          script.incomingMessage(user, msg);
        }
      }
    } catch (NullPointerException e) {
      MUSLog.Log(e.toString(), MUSLog.kSys);
      MUSLog.Log("Null exception in Dispatcher: " + msg, MUSLog.kSys);
      StringBuilder exception = new StringBuilder();
      for (StackTraceElement element : e.getStackTrace()) {
        exception.append(element.toString()).append("\n");
      }
      MUSLog.Log("Stacktrace: " + exception, MUSLog.kSys);
    }

  }

  public void handleGroupAttributeMsg(ServerUser user, String attrcommand, MUSMessage msg, MUSMessage reply) {
    LValue msgcont = msg.m_msgContent;
    if (msgcont.getType() != LValue.vt_PropList) {
      // Error, we need a proplist
      reply.m_errCode = MUSErrorCode.BadParameter;
      reply.m_msgContent = msgcont;
      user.sendMessage(reply);
      return;
    }
    LPropList plist = (LPropList) msgcont;
    try {
      LValue msggroups = plist.getElement(new LSymbol("group"));

      LList groups = new LList();
      GetGroupListFromContents(groups, msggroups);
      LPropList cl = new LPropList();
      LString groupname = new LString();

      for (int e = 0; e < groups.count(); e++) {
        try {
          groupname = (LString) groups.getElementAt(e);
          MUSGroup thisgroup = getGroup(groupname.toString());

          LValue ret = thisgroup.srvcmd_handleAttributeMessage(reply, attrcommand, plist);
          cl.addElement(new LString(groupname.toString()), ret);

        } catch (GroupNotFoundException | MUSErrorCode gnf) {
          LPropList tl = new LPropList();
          tl.addElement(new LSymbol("errorCode"), new LInteger(MUSErrorCode.InvalidGroupName));
          reply.m_errCode = MUSErrorCode.MessageContainsErrorInfo;
          cl.addElement(new LString(groupname.toString()), tl);
        }
        reply.m_msgContent = cl;

      }

    } catch (PropertyNotFoundException | MUSErrorCode pnf) {
      // Error
      reply.m_errCode = MUSErrorCode.BadParameter;
      reply.m_msgContent = msgcont;
    } finally {
      user.sendMessage(reply);
    }
  }

  // Handles messages to a group
  public void handleGroupMsg(ServerUser user, String recpt, MUSMessage msg) {

    try {
      MUSGroup thisgroup = getGroup(recpt);

      // Safeguard against messages to @AllUsers
      if (thisgroup == allUsers) {
        if (user.userLevel() < properties.getIntProperty("MessagingAllUserLevel")) return;
      }

      MUSMessage reply = new MUSMessage(msg);
      reply.m_timeStamp = server.timeStamp();
      reply.m_recptID = new MUSMsgHeaderStringList();
      reply.m_recptID.addElement(new MUSMsgHeaderString(thisgroup.m_name));
      reply.m_udp = msg.m_udp;
      thisgroup.sendMessage(reply);
    } catch (GroupNotFoundException gnf) {
      // MUSLog.Log("Group not found in handleGroupMsg " + recpt, MUSLog.kDeb);
    } catch (MUSErrorCode err) {
      // MUSLog.Log("MUS Error in handleGroupMsg " + err, MUSLog.kDeb);
    }
  }

  public void srvcmd_joinGroup(ServerUser user, String gname) throws MUSErrorCode {
    MUSGroup mg;
    try {
      if (!gname.startsWith("@"))
        throw new MUSErrorCode(MUSErrorCode.InvalidGroupName);

      if (gname.equalsIgnoreCase("@AllUsers"))
        throw new MUSErrorCode(MUSErrorCode.InvalidGroupName);

      if (!isGroupAllowed(gname))
        throw new MUSErrorCode(MUSErrorCode.ErrorJoiningGroup);

      mg = (MUSGroup) user.serverMovie().getServerGroup(gname);
    } catch (GroupNotFoundException gnf) {
      mg = new MUSGroup((MUSMovie) user.serverMovie(), gname);
    }

    mg.addUser(user);
  }

  public void srvcmd_leaveGroup(ServerUser user, String gname) throws MUSErrorCode {
    MUSGroup mg;
    try {
      if (!gname.startsWith("@"))
        throw new MUSErrorCode(MUSErrorCode.ErrorLeavingGroup);

      mg = getGroup(gname);
      if (gname.equalsIgnoreCase("@AllUsers"))
        throw new MUSErrorCode(MUSErrorCode.ErrorLeavingGroup);

      // Check if we are really members of the group
      boolean isMember = false;

      for (String groupname : user.getGroupNames()) {
        if (gname.equalsIgnoreCase(groupname)) {
          isMember = true;
          break;
        }
      }

      if (!isMember)
        throw new MUSErrorCode(MUSErrorCode.ErrorLeavingGroup);

      mg.removeUser(user);

    } catch (GroupNotFoundException gnf) {
      throw new MUSErrorCode(MUSErrorCode.ErrorLeavingGroup);
    }
  }

} 