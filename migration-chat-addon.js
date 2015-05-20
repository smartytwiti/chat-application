var chatDb;
if (!(typeof dbName === "undefined") && dbName) {
  var connStr = "";
  if (!(typeof host === "undefined") && host) {
    connStr += host;
  }
  if (!(typeof port === "undefined") && port) {
    connStr += ":" + port;
  }
  if (connStr != "") {
    chatDb = connect(connStr+"/"+dbName);
    if (!(typeof uname === "undefined") && uname && !(typeof passwd === "undefined") && passwd) {
      chatDb.auth(uname, passwd);
    }
  } else {
    var conn = new Mongo();
    chatDb = conn.getDB(dbName);
  }
  print("====== Connect to database "+dbName+" successfully");
} else {
  throw new Error("Database name to migration is missing");
}

var roomTypes = ["u", "s", "t", "e"];
for (var i=0; i<roomTypes.length; i++) {
  print("====== Start migrating rooms which type is "+roomTypes[i]);
  migrateRoom(roomTypes[i]);
}

function migrateRoom(roomType) {
  if (!isCollectionExist(dbName, "room_"+roomType)) {
    chatDb.createCollection("room_"+roomType);
  }
  var rooms = chatDb.room_rooms.find({"type":roomType});
  // Add 'roomId' field to all documents of a room which type is given
  // Move all documents of that room to room_{type}
  // Remove migrated room
  rooms.forEach(function(room) {
    var roomId = room._id;
    var roomName = "room_"+roomId;
    if (isCollectionExist(dbName, roomName)) {
      print("====== Start migrating collection room_"+roomId);
      var addRoomIdToMessages = "chatDb."+roomName+".update({}, {$set: {\"roomId\": \""+roomId+"\"}}, false, true)";
      eval(addRoomIdToMessages);

      var insertAllMessages = "chatDb."+roomName+".find().forEach(function(doc){chatDb.room_"+roomType+".insert(doc)})";
      eval(insertAllMessages);
    
      var dropRoom = "chatDb."+roomName+".drop()";
      eval(dropRoom);
      print("====== End migrating collection room_"+roomId);
    } else {
      print("====== Collection room_"+roomId+" no longer exists");
    }
  });
  print("====== End migrating rooms which type is "+roomType);
  print("==================================================");
}

function isCollectionExist(nameOfDB, collection) {
  var collections = chatDb.system.namespaces.find({"name": nameOfDB+"."+collection}).toArray();
  if (collections.length > 0) {
    return true;
  } else {
    return false;
  }
}
