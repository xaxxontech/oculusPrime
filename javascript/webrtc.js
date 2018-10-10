'use strict';

var configuration = null; // { iceServers: [{ urls: "stuns:stun.example.org" }] };
var pc;
var isClient;
var stream = null;


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
		
		pc.createOffer().then(function (offer) {
			return pc.setLocalDescription(offer);
		})
		.then(function () {
			// send the offer to the other peer
			send(JSON.stringify({ desc: pc.localDescription }));
		})
		.catch(logError);  
	}

}

function processMessage(message) {

    if (message.desc) {
        var desc = message.desc;

        // if we get an offer, we need to reply with an answer
        if (desc.type == "offer") {
            pc.setRemoteDescription(desc).then(function () {
                return pc.createAnswer();
            })
            .then(function (answer) {
                return pc.setLocalDescription(answer);
            })
            .then(function () {
				trace("sending: answer");
                var str = JSON.stringify({ desc: pc.localDescription });
                send(str);
            })
            .catch(logError);
        } else if (desc.type == "answer") {
            pc.setRemoteDescription(desc).catch(logError);
        } else {
            trace("Unsupported SDP type. Your code may differ here.");
        }
        
    } else if (message.start) {
		// trace("sending offer");
		
		// pc.createOffer().then(function (offer) {
			// return pc.setLocalDescription(offer);
		// })
		// .then(function () {
			// send(JSON.stringify({ desc: pc.localDescription }));
		// })
		// .catch(logError);  
		
		pc.addTrack(stream.getVideoTracks()[0], stream);  
		
    } else {
        pc.addIceCandidate(message.candidate).then(function() {
			trace("candidate added.");
			}).catch(logError);
	}
}

function setServer() {
	isClient = false;
	
	createPeerConnection();
	
	// get a local stream, show it in a self-view and add it to be sent
	navigator.mediaDevices.getUserMedia({ video: true })
		.then(function (s) {
			localVideo.srcObject = s;
			stream = s;
			// pc.addTrack(stream.getVideoTracks()[0], stream);
		})
		.catch(logError);
			
	getxmlhttp("/oculusPrime/webRTCServlet?clearvars");
	msgPollTimeout = setTimeout("checkForMsg();", MSGPOLLINTERVAL);
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
