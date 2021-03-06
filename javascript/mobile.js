
var pingtimer;
var PINGINTERVAL = 5000;
var webrtcinit = false;
var mobile;
var lastcommand="";
var menus = [ "main_menu", "advanced_menu", "command_log_menu", "navigation_menu", "waypoints_menu",
	"routes_menu" ];

// statuses
// var UNKNOWN = "<span style='color: #999999'>unknown</span>";
// var connection_status = stream_status = cameratilt_status = battery_status = dock_status = UNKNOWN;
// var user_status = null;


function mobileloaded() { // called by body onload

	mobile = mobileAndTabletcheck();
	mobilelog("mobile="+mobile);
	commClientLoaded(); 
	document.getElementById("main_menu").style.display="none";
	videologo("on");
	setstatusunknown();
	
	if (/auth=/.test(document.cookie)) commLoginFromCookie(); 
	else login();
}


function driverexit() { // called by body unload
	callServer("driverexit","");
}


function login() {
	
	logindisplay("on");
	document.getElementById("user").focus();	
}

function logout() {
	eraseCookie("auth");
	callServer("logout","");
	setTimeout("window.location.reload()", 250);
}

function loginsend() {
	var str1= document.getElementById("user").value;
	var str2= document.getElementById("pass").value;
	var str3= document.getElementById("user_remember").checked;
	if (str3 == true) { str3 = "remember"; }
	else { eraseCookie("auth"); }
	
	commLogin(str1, str2, str3); // comm_client.js
	
	logindisplay("off");
}

function logindisplay(state) {
	if (state != "on" && state != "off") {
		state = (document.getElementById("login").style.display == "none") ? "off" : "on";
	}
	
	var logindisplay = "none";
	var othersdisplay = "";
	if (state=="on") {
		logindisplay = "";
		othersdisplay = "none";
	}
	
	document.getElementById("videologo").style.display = othersdisplay;
	document.getElementById("stream").style.display = othersdisplay;

	document.getElementById("steeringcontrols").style.display = othersdisplay;
	document.getElementById("morecontrols").style.display = othersdisplay;
	document.getElementById('controlsdivider').style.display = othersdisplay;

	document.getElementById('statusbox').style.display = othersdisplay;
	document.getElementById('messagebox').style.display = othersdisplay;
	
	document.getElementById("login").style.display = logindisplay;
	
}

function callServer(fn, str) {
	callServerComm(fn, str);
	
	clearTimeout(pingtimer);
	pingtimer = setTimeout("callServer('statuscheck','');", PINGINTERVAL);
	
	mobilelog("callServer: "+fn+", "+str);
}


function message(message, colour, status, value) {
	
	if (status != null) {   
		if (status == "multiple") { setstatusmultiple(value); }
		else { setstatus(status, value); }
	}

	if (!webrtcinit && ws_server && ws_port && turnserver_login && turnserver_port) {
		webrtcinit = true;
		websocketServerConnect(); // webrtc.js
	}
	
	if (!pingtimer) 
		pingtimer = setTimeout("callServer('statuscheck','');", 500);
		
	if (message != null && message != "serverok") {
		var mb = document.getElementById('messagebox');
		var str = mb.innerHTML;
		mb.innerHTML = "<span style='color: #444444'>:</span><span style='color: "
			+ colour + "';>" + message + "</span>" + "<br>" + str; 
	}
	
}


function setstatusmultiple(value) {
	var statusarray = new Array();
	statusarray = value.split(" ");
	for (var i = 0; i<statusarray.length; i+=2) {
		setstatus(statusarray[i], statusarray[i+1]);
	}
}


function setstatus(status, value) {
	
	var s;
	if (s= document.getElementById(status+"_status"))  s.innerHTML = value;

	if (status == "storecookie") createCookie("auth",value,30); 
	else if (status == "connection") {
		if (value == "closed") connectionlost();
		else if (value == "connected") { 
			document.getElementById("login").style.display = "none";
			videologo("on");
		}
	}

	else if (status=="webrtcserver") { ws_server = value; } // webrtc.js
	else if (status=="webrtcport") { ws_port = value; } // webrtc.js
	else if (status=="turnserverlogin") {turnserver_login = value; } // webrtc.js
	else if (status=="turnserverport") {turnserver_port = value; } // webrtc.js

	else if (status == "storecookie") createCookie("auth",value,30); 
	if (status == "stream") {
		if (value == "stop" || value == "mic")  videologo("on"); 
	}

}


function steeringmousedown(id) {
	var button=document.getElementById(id);
	if (mobile) { 
		button.onmousedown = function(){}; // disable mouse events, prevent double action
		button.onmouseup = function(){};
	}
	
	var fwhighlitebox = document.getElementById("forwardhighlitebox");
	var fwhighliteboxdisplay = true;
	var xy = findpos(button);

	if (id == "forward") { 
		callServer("move", "forward");
		fwhighlitebox.style.left = xy[0]+"px";
		fwhighlitebox.style.top = xy[1]+"px";
		fwhighlitebox.style.width = button.offsetWidth+"px";
		fwhighlitebox.style.height = button.offsetHeight+"px";
		fwhighlitebox.style.display = "";
	}
	
	var highlitebox = document.getElementById("mousecontrolshighlitebox");
	highlitebox.style.left = xy[0]+"px";
	highlitebox.style.top = xy[1]+"px";
	highlitebox.style.width = button.offsetWidth+"px";
	highlitebox.style.height = button.offsetHeight+"px";
	highlitebox.style.display = "";	
	
	if (id == "camera up") { callServer("cameracommand", "up"); }
	else if (id == "camera down") { callServer("cameracommand", "down");    }
	else if (id == "camera horizontal") { callServer("cameracommand", "horiz");  }
	else if (id == "menu button") { menu('main_menu'); highlitebox.style.display = "none";	
 }
	else if (id == "publish camera") { callServer("publish", "camera");  }
	else if (id == "publish stop") { callServer("publish", "stop");  }
	else if (id == "autodock go") { callServer("autodock", "go");  }
	
	else if (id == "backward") { callServer("move", "backward"); fwhighliteboxdisplay = false; }
	else if (id == "rotate right") { callServer("move", "right"); fwhighliteboxdisplay = false; }
	else if (id == "rotate left") { callServer("move", "left"); fwhighliteboxdisplay = false; }
	else if (id == "nudge right") { callServer("nudge", "right"); fwhighliteboxdisplay = false; }
	else if (id == "nudge left") { callServer("nudge", "left"); fwhighliteboxdisplay = false; }
	else if (id == "nudge forward") { callServer("nudge", "forward"); fwhighliteboxdisplay = false; }
	else if (id == "nudge backward") { callServer("nudge", "backward"); fwhighliteboxdisplay = false; }
	else if (id == "stop") { callServer("move", "stop"); fwhighliteboxdisplay = false; }

	if (!fwhighliteboxdisplay) fwhighlitebox.style.display = "none";
}


function steeringmouseup(id) {

	document.getElementById("mousecontrolshighlitebox").style.display = "none";		
	if (id == "backward" || id=="rotate right" || id=="rotate left") { callServer("move", "stop"); }
	else if (id == "camera up" || id=="camera down") callServer("cameracommand", "stop");

}


function findpos(obj) { // derived from http://bytes.com/groups/javascript/148568-css-javascript-find-absolute-position-element
	var left = 0;
	var top = 0;
	var ll = 0;
	var tt = 0;
	while(obj) {
		lb = parseInt(obj.style.borderLeftWidth);
		if (lb > 0) { ll += lb }
		tb = parseInt(obj.style.borderTopWidth);
		if (tb > 0) { tt += tb; }
		left += obj.offsetLeft;
		top += obj.offsetTop;
		obj = obj.offsetParent;
	}
	left += ll;
	top += tt;
	return [left,top];
}


function readCookie(name) {
	var nameEQ = name + "=";
	var ca = document.cookie.split(';');
	for(var i=0;i < ca.length;i++) {
		var c = ca[i];
		while (c.charAt(0)==' ') c = c.substring(1,c.length);
		if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
	}
	return null;
}

function createCookie(name,value,days) {
	if (days) {
		var date = new Date();
		date.setTime(date.getTime()+(days*24*60*60*1000));
		var expires = "; expires="+date.toGMTString();
	}
	else var expires = "";
	document.cookie = name+"="+value+expires+"; path=/";
}

function eraseCookie(name) {
	createCookie(name,"",-1);
}

function keypress(e) {
	var keynum;
	if (window.event) {
		keynum = e.keyCode;
	}// IE
	else if (e.which) {
		keynum = e.which;
	} // Netscape/Firefox/Opera
	return keynum;
}

function connectionlost() {
	document.title = "closed";
	connected = false;
	clearTimeout(pingtimer);
	videologo("on");
	setstatusunknown();
}

function videologo(state) {
	
	mobilelog("videologo("+state+")");
	
	var pd = document.getElementById("pagediv");
	// if (pd.offsetWidth > pd.offsetHeight * 0.8) pd.style.width = Math.floor(pd.offsetHeight * 0.8) + "px";

	var vidlogo = document.getElementById("videologo");
	var vid = document.getElementById("stream");
	
    var xyvid = findpos(vid);

    vidlogo.style.top = xyvid[1] + "px";
	vidlogo.style.left = xyvid[0] + "px";
	vidlogo.style.width = vid.offsetWidth + "px";
	vidlogo.style.height = vid.offsetHeight + "px";

	if (state=="on") {
		vid.style.height=Math.ceil(vid.offsetWidth*9/16) + "px";
		vidlogo.style.display = ""; 
	}
	else if (state=="off") { 
		vidlogo.style.display = "none"; 
	}
	
	// position message box
	var sc = document.getElementById("steeringcontrols");
	var mc = document.getElementById("morecontrols");
	var cd = document.getElementById('controlsdivider');

	var sb = document.getElementById('statusbox');
	var mb = document.getElementById('messagebox');
	// var lg = document.getElementById('login');

	var xy = findpos(sc);
	
	// steeringcontrols height
	sc.style.height = (document.body.offsetHeight - vid.offsetHeight - mc.offsetHeight - cd.offsetHeight)*0.95 + "px";
	
	// statusbox size
	sbratio = 0.15;
	sb.style.left = xy[0] + Math.floor(sc.offsetWidth*0.1) + "px";
	sb.style.top = xy[1] + Math.floor(sc.offsetHeight*sbratio) + "px";
	sb.style.width = Math.floor(sc.offsetWidth*0.8) + "px";
	// sb.style.display="";
	
	// messagebox size
	mb.style.left = xy[0] + Math.floor(sc.offsetWidth*0.1) + "px";
	mb.style.top = xy[1] + Math.floor(sc.offsetHeight*sbratio) + sb.offsetHeight + "px";
	mb.style.width = Math.floor(sc.offsetWidth*0.8) + "px";
	mb.style.height = Math.floor(sc.offsetHeight*(1-sbratio)) + cd.offsetHeight + mc.offsetHeight - sb.offsetHeight + "px";
	// mb.style.display="";

	// menus overlay
	for (var i = 0; i<menus.length; i++) {
		var mm = document.getElementById(menus[i]);
		mm.style.left = xy[0] + "px";
		mm.style.top = xyvid[1] + vid.offsetHeight/2 + "px";
		mm.style.width =  Math.floor(sc.offsetWidth*0.9) +"px";
		mm.style.height = Math.floor((vid.offsetHeight/2 + sc.offsetHeight + cd.offsetHeight + mc.offsetHeight)*0.99) + "px";
	}

}

function menu(id) {
	
	closemenus();
	
	document.getElementById(id).style.display = "";
	if (id=="main_menu") {
		// document.getElementById('telnetcommand').focus();
		document.getElementById('telnetcommand').value=lastcommand;
	}
	
}

function closemenus() {
	for (var i = 0; i<menus.length; i++) {
		document.getElementById(menus[i]).style.display = "none";
	}
	
	videologo();
}

function telnetsend() {
	str = document.getElementById("telnetcommand").value;
	str = str.replace(/^\s+|\s+$/g, ''); // strip
	lastcommand = str;
	var cmd = str.split(" ",1);
	var val = str.substring(cmd[0].length+1);
	callServer(cmd[0], val);
	message("sending: "+cmd[0]+" "+val,"orange");
	mobilelog("telnetsend() "+str);
}

function telnetclear() {
	document.getElementById("telnetcommand").value = "";
}

function setstatusunknown() {
	var statuses = document.getElementsByClassName("status");
	for (var i = 0; i < statuses.length; i++) {
		statuses[i].innerHTML = "<span class='grey'>unknown</span>";
	}
	document.getElementById("connection_status").innerHTML = "<span class='grey'>CLOSED</span>";
}

function showcommandlog() {
	document.getElementById("command_log_box").innerHTML = document.getElementById("messagebox").innerHTML;
	menu("command_log_menu");
}

function mobileAndTabletcheck() {
	check = false;
	(function(a){if(/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(a)||/1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(a.substr(0,4))) check = true;})(navigator.userAgent||navigator.vendor||window.opera);
	return check;
}

function openxmlhttp(theurl, functionname) {
	  if (window.XMLHttpRequest) {// code for all new browsers
	    xmlhttp=new XMLHttpRequest();}
	  else if (window.ActiveXObject) {// code for IE5 and IE6
	    xmlhttp=new ActiveXObject("Microsoft.XMLHTTP"); 
	    theurl += "?" + new Date().getTime();
	  }
	  if (xmlhttp!=null) {
	    xmlhttp.onreadystatechange=functionname; // event handler function call;
	    xmlhttp.open("GET",theurl,true);
	    xmlhttp.send(null);
	  }
	  else {
	    alert("Your browser does not support XMLHTTP.");
	  }
}

function mobilelog(str) {
	//console.log(str);
}
