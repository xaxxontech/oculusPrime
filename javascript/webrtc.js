'use strict';

var configuration = null; // { iceServers: [{ urls: "stuns:stun.example.org" }] };
var pc;
var isClient;
var stream = null;
var pendingIceCanditateMessages = [];
var setRemoteDescriptionComplete = false; // used by server only 

function createPeerConnection() {
	pc = new RTCPeerConnection(configuration);
	    
	// send any ice candidates to the other peer
    pc.onicecandidate = function (evt) {
		if(evt.candidate) {
			trace("sending candidate");
			send(JSON.stringify({ candidate: evt.candidate }));
		}
    };

	pc.oniceconnectionstatechange = function(evt) {
		const t = evt.target;
		console.log('ICE state change event: ', evt);
		trace("ICE state: "+t.iceConnectionState);
	};

    pc.onnegotiationneeded = function () {
		trace("negotiation needed");
		trace("sending offer");
		
		/*
		pc.createOffer().then(function (offer) {
			trace("offer");
			trace(JSON.stringify(offer));
			
			return pc.setLocalDescription(offer);
		})
		.then(function () {
			trace("pc.localDescription");
			trace(JSON.stringify({ desc: pc.localDescription }));
			
			// asdf = setMediaBitrates({ desc: pc.localDescription });
			// send(JSON.stringify(asdf));
			
			// send the offer to the other peer
			send(JSON.stringify({ desc: pc.localDescription }));
		})
		.catch(logError);  
		*/
		
		pc.createOffer().then(function (offer) {
			pc.setLocalDescription(offer);
			// offer.sdp = setMediaBitrates(offer.sdp);
			offer.sdp = forceCodec(offer.sdp);
			send(JSON.stringify({ desc: offer}));
		}).catch(logError);  
	}
}

function processMessage(message) {

    if (message.desc) {
        var desc = message.desc;

        if (desc.type == "offer") { // should be received by client only 
            pc.setRemoteDescription(desc).then(function () {
				setRemoteDescriptionComplete = true;
                return pc.createAnswer();
            })
            // .then(function (answer) {
                // return pc.setLocalDescription(answer);
            // })
            // .then(function () {
				// trace("sending: answer");
                // var str = JSON.stringify({ desc: pc.localDescription });
                // send(str);
				// processPendingIceCanditateMessages(); 
            // })
            // .catch(logError);
            
            .then(function(answer) {
				pc.setLocalDescription(answer);
				trace("sending: answer");
				// answer.sdp = setMediaBitrates(answer.sdp);
				answer.sdp = forceCodec(answer.sdp);
				send(JSON.stringify({ desc: answer}));
				processPendingIceCanditateMessages(); 
			}).catch(logError);
			
        } 
        else if (desc.type == "answer") { // should be received by server only 
            pc.setRemoteDescription(desc).then(function() {
				setRemoteDescriptionComplete = true;
				processPendingIceCanditateMessages();
			}).catch(logError);
        } 
        else {
            trace("Unsupported SDP type. Your code may differ here.");
        }
    }

    else if (message.start) { // should be received by server only 
		getCamera();
		// pc.addTrack(stream.getVideoTracks()[0], stream);  
    } 
    
    else { // if get to here, should be ice candidates only 

		// firefox 62 throws error here on server only if setRemoteDescription not called first
		// doing for both client and server seems to be 100% connect (firefox-firefox)
		if (!setRemoteDescriptionComplete) {
			trace("*** delaying icecandidate ***");
			pendingIceCanditateMessages.push(message); // save for processing later
		}
		
		else {
			pc.addIceCandidate(message.candidate).then(function() {
				trace("candidate added.");
				}).catch(logError);
		}
	}
}

function processPendingIceCanditateMessages() {
	for (var i=0; i<pendingIceCanditateMessages.length; i++) {
		trace("*** processing delayed icecandidate: "+i+" ***");
		processMessage(pendingIceCanditateMessages[i]);
	}
}

function setServer() {
	isClient = false;
	
	createPeerConnection();
	
	// getCamera();
			
	getxmlhttp("/oculusPrime/webRTCServlet?clearvars");
	msgPollTimeout = setTimeout("checkForMsg();", MSGPOLLINTERVAL);
}

function getCamera() {
	// get a local stream, show it in a self-view and add it to be sent
	// causes onnegotiationneeded event
	// navigator.mediaDevices.getUserMedia({ video: true })
	var constraints = { video: {
		frameRate: {max: 15 },
		width: 640,
		height: 480
	 } };
	navigator.mediaDevices.getUserMedia(constraints)
		.then(function (s) {
			localVideo.srcObject = s;
			stream = s;
			pc.addTrack(stream.getVideoTracks()[0], stream);
	}).catch(logError);
}

function setClient() {
	isClient = true;
	
	createPeerConnection();
	
	// once remote track arrives, show it in the remote video element
	pc.ontrack = function (evt) {
		// don't set srcObject again if it is already set.
		if (!remoteVideo.srcObject)
			remoteVideo.srcObject = evt.streams[0];
	};
	
	// send offer when button clicked
	let callButton = document.getElementById('callButton');
	callButton.onclick = function() {
		// trace("sending offer");
		
		// pc.createOffer( {offerToReceiveVideo: 1} ).then(function (offer) {
			// return pc.setLocalDescription(offer);
		// })
		// .then(function () {
			// //send the offer to the other peer
			// send(JSON.stringify({ desc: pc.localDescription }));
		// })
		// .catch(logError);
		
		trace("sending: start");
		send(JSON.stringify({ start: true }));
	}
	
	getxmlhttp("/oculusPrime/webRTCServlet?clearvars");
}


/* messaging nuts and bolts below */

var msgcheckpending = false;
var msgssent = 0;
let msgsrcvd = 0;
let msgPollTimeout = null;
const MSGPOLLINTERVAL = 500;

function msgReceived(xhr) { 

	if (xhr.readyState==4) {// 4 = "loaded"
		if (xhr.status==200) {// 200 = OK
			
			clearTimeout(msgPollTimeout); msgPollTimeout = null;
			msgcheckpending = false;
			
			if (xhr.responseText == "") { // no msg
				checkForMsg();
				return;
			}
			
			// msg recevied
			msgsrcvd ++;
			trace ("messages sent/received: "+msgssent+", "+msgsrcvd);
			trace("webRTCServletResponse:\n"+xhr.responseText);

			var message = JSON.parse(xhr.responseText);
			
			processMessage(message);
			
			checkForMsg();

		}
	}
}

function send(msg) {
	if (isClient)
		postxmlhttp("/oculusPrime/webRTCServlet?msgfromclient="+msg, msg);
	else 
		postxmlhttp("/oculusPrime/webRTCServlet?msgfromserver="+msg, msg);
}

function checkForMsg() {
	
	if (msgcheckpending) return;
	
	msgcheckpending = true;
	
	if (isClient)
		getxmlhttp("/oculusPrime/webRTCServlet?requestservermsg");
	else 
		getxmlhttp("/oculusPrime/webRTCServlet?requestclientmsg");
	
}

function getxmlhttp(theurl) {
	let xmlhttp = new XMLHttpRequest(); 
	xmlhttp.onreadystatechange=function() { msgReceived(xmlhttp); } // event handler function call;
	xmlhttp.open("GET",theurl,true);
	xmlhttp.send();
}

function postxmlhttp(theurl, data) {

	let xmlhttp=new XMLHttpRequest();

	xmlhttp.onreadystatechange = function() { msgReceived(xmlhttp); }; // event handler function call;
	xmlhttp.open("POST",theurl,true);
	xmlhttp.send(data);
	
	msgssent ++;
	trace ("messages sent/received: "+msgssent+", "+msgsrcvd);

}


function logError(error) {
    trace("***ERROR****"+error.name + ": " + error.message);
}

function trace(text) {
	text = text.trim();
	if (isClient) text = "CLIENT: "+text;
	else text = "SERVER: "+text;
	const now = (window.performance.now() / 1000).toFixed(3);
	console.log(now, text);
}

/* from https://webrtchacks.com/limit-webrtc-bandwidth-sdp/ */
function setMediaBitrates(sdp) {
  return setMediaBitrate(setMediaBitrate(sdp, "video", 500), "audio", 50);
}
function setMediaBitrate(sdp, media, bitrate) {
  var lines = sdp.split("\n");
  var line = -1;
  for (var i = 0; i < lines.length; i++) {
    if (lines[i].indexOf("m="+media) === 0) {
      line = i;
      break;
    }
  }
  if (line === -1) {
    console.debug("Could not find the m line for", media);
    return sdp;
  }
  console.debug("Found the m line for", media, "at line", line);
 
  // Pass the m line
  line++;
 
  // Skip i and c lines
  while(lines[line].indexOf("i=") === 0 || lines[line].indexOf("c=") === 0) {
    line++;
  }
 
  /* If we're on a b line, replace it */
  if (lines[line].indexOf("b") === 0) {
    console.debug("Replaced b line at line", line);
    lines[line] = "b=TIAS:"+bitrate;
    return lines.join("\n");
  }
  
  /* Add a new b line */
  console.debug("Adding new b line before line", line);
  var newLines = lines.slice(0, line)
  newLines.push("b=TIAS:"+bitrate)
  newLines = newLines.concat(lines.slice(line, lines.length))
  return newLines.join("\n")
}

function forceCodec(sdp) {
	var lines = sdp.split("\n");
	var newlines = [];

	for (var i = 0; i < lines.length; i++) {
		if (lines[i].includes("VP9") || lines[i].includes("VP8")) continue; // VP8 VP9 H264
		newlines.push(lines[i]);
	}
	return newlines.join("\n");
}
