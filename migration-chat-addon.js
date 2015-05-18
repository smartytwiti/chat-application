var chatDb;
if (hostname && dbName) {
  chatDb = connect(hostname + "/" + dbName);
} else if (dbName) {
  var conn = new Mongo();
  chatDb = conn.getDB(dbName);
} else {
  return;
}

var roomTypes = ["u", "s", "t", "e"];
for (var i=0; i<roomTypes.length; i++) {
  migrateRoom(roomTypes[i]);
}

function migrateRoom(roomType) {
  chatDb.createCollection("room_"+roomType);
  var rooms = chatDb.room_rooms.find({"type":roomType});
  // Add 'roomId' field to all documents of a room which type is given
  // Move all documents of that room to room_{type}
  // Remove migrated room
  rooms.forEach(function(room) {
    var roomId = room._id;
    var addRoomIdToMessages = "chatDb.room_" + roomId + ".update({}, {$set: {\"roomId\": \"" + roomId + "\"}}, false, true)";
    eval(addRoomIdToMessages);

    var insertAllMessages = "chatDb.room_" + roomId + ".find().forEach(function(doc){chatDb.room_" + roomType + ".insert(doc)})";
    eval(insertAllMessages);
    
    var dropRoom = "chatDb.room_" + roomId + ".drop()";
    eval(dropRoom);
  });
}


