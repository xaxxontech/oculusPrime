var mapzoom = null; //future: set from cookie
var mapimgdivwidth = 480; //future: set from cookie
var mapimgdivheight = 480; //future: set from cookie
var mapimgleft = 0; //future: set from cookie
var mapimgtop = 0; //future: set from cookie
var mapzoomtimer;
var rosmapinfotimer = null;
var rosmapimgleftstart = null;
var rosmapimgtopstart = null;
var rosmapimggrabstartxy = null;
//var rosimgloading = true;
var rosmapimgnext = new Image();
var rosmapupdate = null;
var robotx = 0;
var roboty = 0; 
var robotsize = 0.3;
var amcloffstx = 0;
var amcloffsty = 0;
var amcloffstth = 0;
var rosodomx = 0;
var rosodomy = 0;
var rosodomth = 0;
var mapinfo=[0,0,0,0,0,0];
var odom=[0,0,0];
var globalpath = null;
var maparrowpose = null;
var rosmaparrowmode = null;
var mapwaypoint = null;
var laserscan = null;
var waypointsettime = 0; 

function rosmap(mode) {
	
	var v = document.getElementById("main_menu_over");
	var xy = findpos(v);
	var x = xy[0] + v.offsetWidth;
	var y=findpos(document.getElementById("video"))[1];
	var date = new Date().getTime();
	var str = document.getElementById("rosmap_menu_hiddencontents").innerHTML;
	var img = new Image();
	img.src = 'frameGrabHTTP?mode=rosmap&date='+ date;
	
	img.onload= function() {
		// defaults
		var width = mapimgdivwidth;
		if (width == null ) width = 480;
		var height = mapimgdivheight;
		if (height == null) height = 480;

		var zoom = mapzoom;
		if (zoom == null) {
			if (img.naturalWidth/width > img.naturalHeight/height) {
				zoom = width/img.naturalWidth;
				mapzoom = zoom;
			}
			else zoom = height/img.naturalHeight;
		}
		
		var left = mapimgleft;
		if (left == null) left = -((img.naturalWidth * zoom)-width)/2; // center default
		var top = mapimgtop;
		if (top == null) top = -((img.naturalHeight * zoom)-height)/2; // center default	
		
		str += "<div id='rosmapimgdiv' style='width: "+width+"px; height: "+height+"px; "; // img div
		str += "overflow: hidden;'>";
		
		str += "<div style='height: 0px; width: 0px; position: relative; z-index: 4'>";
		str += "<canvas id='rosmaparrow' style='position: relative'></canvas></div>";
		
		str += "<div style='height: 0px; width: 0px; position: relative; z-index: 5'>";
		str += "<canvas id='rosmaprobot' style='cursor: move; position: relative'></canvas></div>";
		
		str += "<img id='rosmapimg' src='frameGrabHTTP?mode=rosmap&date=" + date + "' " ;
		str += "width='" + img.naturalWidth * zoom +"' ";
		str += "height='" + img.naturalHeight * zoom +"' ";
		str += "style='position: relative; left: "+left+"px; top: "+top+"px; '";
		str +=	"alt=''></div>";
		popupmenu('rosmap', 'show', x, y, str, width, 1, null );
		
		// drag
		var rmi = document.getElementById("rosmaprobot"); // was rosmapimg
//		rmi.ondragstart = function() { return false; };
		rmi.onmousedown = function(event) {
			rosmapimggrabstartxy = getmousepos(event);
			rosmapimgleftstart = mapimgleft;
			rosmapimgtopstart = mapimgtop;
			var i = document.getElementById("rosmaprobot");
			i.onmousemove = function(event) { rosmapimgdrag(event); }
			i.onmouseout = function() { 
				document.getElementById("rosmaprobot").onmousemove = null; }
		}
		rmi.onmouseup = function() { 
			document.getElementById("rosmaprobot").onmousemove = null; }

		rosmapupdate = null;
		openxmlhttp("frameGrabHTTP?mode=rosmapinfo", rosinfo);
	}
	
}

function rosmapImgReload() {
	if (document.getElementById("rosmap_menu_over").style.display != "") return;
	
	rosimgreloadtimer = null; // ?
	date = new Date().getTime();
	rosmapimgnext.src = "frameGrabHTTP?mode=rosmap&date="+date;
	rosmapimgnext.onload = function() {
		var img = document.getElementById('rosmapimg');
		img.src = rosmapimgnext.src;
	}
}

function rosinfo() {
	if (document.getElementById("rosmap_menu_over").style.display != "") return;
	
	if (xmlhttp.readyState==4) {// 4 = "loaded"
		if (xmlhttp.status==200) {// 200 = OK
			var str = xmlhttp.responseText;
			//  width_height_res_originx_originy_originth_updatetime odomx_odomy_odomth
			
			var s = str.split(" ");
			
			var nukewaypoint = true;
//			if (s.length == 0) nukewaypoint = false; // in case of empty xmlhttp
			// to prevent cancellening right after setting:
			var t = new Date().getTime();
			if (t - waypointsettime < 5000) nukewaypoint = false;
			
			var rosmaprezoom = false;
			
			for (var i=0; i<s.length; i++) {
				var ss = s[i].split("_");
				
				switch(ss[0]) {
					
					case "rosmapinfo":
						mapinfo = ss[1].split(",");
						break;
						
					case "rosmapupdated": 
						rosmaprezoom = true;
						break;
					
					case "rosamcl":
						var amcl = ss[1].split(",");
						amcloffstx = parseFloat(amcl[0]);
						amcloffsty = parseFloat(amcl[1]);
						amcloffstth = parseFloat(amcl[2]);
						rosodomx = parseFloat(amcl[3]);
						rosodomy = parseFloat(amcl[4]);
						rosodomth = parseFloat(amcl[5]);
						break;
						
					case "rosglobalpath":
						globalpath = ss[1].split(",");
						break;
						
					case "rosscan":
						laserscan = ss[1].split(",");
						break;
						
					case "roscurrentgoal":
						nukewaypoint = false;
						break;
				
				}
			}
			
			if (mapwaypoint != null && nukewaypoint)  {
				mapwaypoint = null;
				rosmaparrow("cancel");
			}
			
			if (rosmaprezoom) { rosmapzoomdraw(mapzoom, 0); }
			else drawmapinfo();
//			drawmapinfo();
			
			var updatetime = parseFloat(mapinfo[6]);
			if (rosmapupdate != null) { 
				if (updatetime > rosmapupdate) rosmapImgReload();
			}
			rosmapupdate = updatetime;
			
			setTimeout("openxmlhttp('frameGrabHTTP?mode=rosmapinfo', rosinfo);", 510);
		}
	}
}

function rosmapimgdrag(ev) {

	var xy = getmousepos(ev);
	var xdelta = xy[0] - rosmapimggrabstartxy[0];
	var ydelta = xy[1] - rosmapimggrabstartxy[1];
	mapimgleft = rosmapimgleftstart +xdelta;
	mapimgtop = rosmapimgtopstart + ydelta;
	var img = document.getElementById("rosmapimg");
	img.style.left = mapimgleft + "px";
	img.style.top = mapimgtop + "px";

	drawmapinfo();
}


function rosmapzoom(mult) {
	var increment = 0.1;
	var steptime = 100;
	if (mult != 0) { 
		var zoom = mapzoom * (1 + increment * mult);
		if (mapzoomtimer == null) steptime = 0;
		mapzoomtimer = setTimeout("rosmapzoomdraw("+zoom+", "+mult+");", steptime);
	}
	else { // cancel
		clearTimeout(mapzoomtimer);
		mapzoomtimer = null;
	}
}

function rosmapzoomdraw(zoom, mult) {
	if (zoom < 0.1 || zoom > 10) return;
	
	var img = document.getElementById("rosmapimg");
	//determine previous center position ratio
	var ctrwidthratio = ((mapimgdivwidth/2)-mapimgleft)/img.width;
	var ctrheightratio = ((mapimgdivheight/2)-mapimgtop)/img.height;
	// set new zoom level:
	img.width = img.naturalWidth * zoom;
	img.height = img.naturalHeight * zoom;
	//set new position:
	
	mapimgleft = (mapimgdivwidth/2)-(img.width * ctrwidthratio);
	mapimgtop = (mapimgdivheight/2)-(img.height * ctrheightratio);
	
	img.style.left = mapimgleft+"px";
	img.style.top = mapimgtop+"px";
	
	mapzoom = zoom;

	drawmapinfo();
	
	rosmapzoom(mult);
}

function drawmapinfo(str) {
	
	//  width_height_res_originx_originy_originth_updatetime odomx_odomy_odomth
	
	var robotcanvas = document.getElementById("rosmaprobot");
	var img = document.getElementById("rosmapimg");

	robotcanvas.width = mapimgdivwidth;
	robotcanvas.height = mapimgdivheight;
	
	var res = parseFloat(mapinfo[2]);  // resolution

	// robot center
	var x = parseFloat(mapinfo[3]) - (rosodomx + amcloffstx);  // x = originx - odomx
	x /= -res;   //   x /= res
	robotx = x; // before scaling and offsets
	x= x * mapzoom + mapimgleft;

	var y = parseFloat(mapinfo[4]) - (rosodomy + amcloffsty);  // y = originy - odomy
	y /= -res;  // y /= res
	y = parseFloat(mapinfo[1])-y;
	roboty = y; // before scaling and offsets
	y = y * mapzoom + mapimgtop;

	// robot angle
	var th = -(parseFloat(mapinfo[5]) + (rosodomth + amcloffstth)); // originth + odomth
	// robot size
	var size = robotsize/parseFloat(mapinfo[2]) * mapzoom; 
	
	var context = robotcanvas.getContext('2d');
	context.translate(x, y);

	if (globalpath) {

		context.rotate(-amcloffstth);

		context.beginPath()
		for (var i=0; i < globalpath.length; i+= 2) {
			
			var pathx = parseFloat(mapinfo[3]) - (parseFloat(globalpath[i]) + amcloffstx);  // x = originx - x
			pathx /= -res;   //   x /= res
			pathx= pathx * mapzoom + mapimgleft;
			pathx -= x;

			var pathy = parseFloat(mapinfo[4]) - (parseFloat(globalpath[i+1]) + amcloffsty);  // y = originy - odomy
			pathy /= -res;  // y /= res
			pathy = parseFloat(mapinfo[1])-pathy;
			pathy = pathy * mapzoom + mapimgtop;
			pathy -= y;
			
			if (i <2)   context.moveTo(pathx, pathy);
			else  context.lineTo(pathx, pathy);
		}
		context.lineWidth = 1;
		context.strokeStyle = "#0000ff";
		context.stroke();
		context.rotate(+amcloffstth);
	} 
		
	context.rotate(th);
	
	if (laserscan) {
		var anglemax = 0.51; // radians
		var anglestep = (anglemax*2)/(laserscan.length-1);
		var angle = anglemax;
		context.fillStyle = "#ff00ff";
		for (i = 0; i < laserscan.length; i++) {
			if (laserscan[i] != "nan") {
				var px = Math.cos(angle)*parseFloat(laserscan[i]) / res * mapzoom;
				var py = Math.sin(angle)*parseFloat(laserscan[i]) / res * mapzoom;
				context.fillRect(px-1, py-1, 3, 3);
			}
			angle -= anglestep;
		}
	}

	// draw robot
	var linewidth = 3;
	var stroke = "#ff0000";
	var fill = "#ffffff";
	
	context.beginPath();
	context.moveTo(size/2, 0);
	context.lineTo(size/2+20, 0);
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	
	context.beginPath();
	context.moveTo(size/2+14,6);
	context.lineTo(size/2+20,0);
	context.lineTo(size/2+14,-6);
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	
	context.beginPath();
	context.moveTo(size / -2, size / -2);
	context.lineTo(size / 4, size / -2);
	context.lineTo(size / 2, size / -6);
	context.lineTo(size /2, size / 6);
	context.lineTo(size / 4, size /2);
	context.lineTo(size / -2, size /2);
	context.lineTo(size / -2, size / -2);
	context.fillStyle = fill;
	context.fill();
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	

	drawmaparrow();
	
}

function rosmaparrow(mode) {
	if (mode == "position" || mode == "waypoint") {
		
		var str = "<a class='blackbg' href='javascript: rosmaparrow(&quot;cancel&quot;)'>";
		str += "<span class='cancelbox'><b>X</b></span> ";
		str += "CANCEL</a>"; 
		document.getElementById("rosmapinfobar").innerHTML = str; 
		
		var robotcanvas = document.getElementById("rosmaprobot");
		robotcanvas.style.cursor = "crosshair";
		
		var arrowcanvas = document.getElementById("rosmaparrow");
		var img = document.getElementById("rosmapimg");

		arrowcanvas.width = mapimgdivwidth;
		arrowcanvas.height = mapimgdivheight;
		
		maparrowpose = null;
		robotcanvas.onmouseover = function() { 
			maparrowpose = [];
			rosmaparrowmode = mode;
			robotcanvas.onmouseover = null;
		}
		
		document.onmousemove = function(event) {
			if (maparrowpose == null) return;
			var xy = getmousepos(event);
			var arxy = findpos(document.getElementById("rosmapimg"));
			maparrowpose[0] = (xy[0]-arxy[0])/mapzoom;
			maparrowpose[1] = (xy[1]-arxy[1])/mapzoom;
			drawmaparrow();
		}

		robotcanvas.onclick = function(event) {
			
			document.onmousemove = function(event) {
				
				var xy = getmousepos(event);
				var arxy = findpos(document.getElementById("rosmapimg"));
				
				var deltax = (xy[0]-arxy[0])/mapzoom - maparrowpose[0];
				var deltay = (xy[1]-arxy[1])/mapzoom - maparrowpose[1];
				maparrowpose[2] = Math.atan(deltay/deltax); // theta
				if (deltax < 0) maparrowpose[2] += Math.PI; 
				drawmaparrow();
			}
			
			robotcanvas.onclick = function(event) {
				// arrow drop complete  
				if (rosmaparrowmode == "waypoint") { 
					mapwaypoint = maparrowpose;
					waypointsettime = new Date().getTime();
					// send waypoint maparrowpose[] to ROS:
					callServer("state","rossetgoal "+torosmeters(mapwaypoint));
					callServer("state","roscurrentgoal pending");
					str = "<a class='blackbg' href='javascript: callServer(&quot;state&quot;, &quot;rosgoalcancel true&quot;)'>";
					str += "<span class='cancelbox'><b>X</b></span> ";
					str += "CANCEL GOAL</a>"; 
					document.getElementById("rosmapinfobar").innerHTML = str; 
				}
				else if (rosmaparrowmode == "position") {
					// send position maparrowpose[] to ROS:
					callServer("state","rosinitialpose "+torosmeters(maparrowpose));
					if (mapwaypoint != null) maparrowpose = mapwaypoint;
					else {
						var arrowcanvas = document.getElementById("rosmaparrow");
						arrowcanvas.width = 0;
						arrowcanvas.height = 0;						
					}
					document.getElementById("rosmapinfobar").innerHTML = "";
				}
				maparrowpose = null;
				document.onmousemove = null;
				robotcanvas.onclick = null;
				robotcanvas.onmouseover = null;
				robotcanvas.style.cursor = "move";
				rosmaparrowmode = null;
			}
		}
		
		drawmaparrow();
	}
	else { // cancel
		document.getElementById("rosmapinfobar").innerHTML = "";
		document.onmousemove = null;
		var rmr = document.getElementById("rosmaprobot")
		rmr.onclick= null;
		rmr.style.cursor = "move";
		rmr.onmouseover = null;
		rosmaparrowmode = null;
		if (mapwaypoint != null) maparrowpose = mapwaypoint;
		else {
			var arrowcanvas = document.getElementById("rosmaparrow");
			arrowcanvas.width = 0;
			arrowcanvas.height = 0;
			maparrowpose = null;
		}
		
	}
}

function drawmaparrow() {
	
	if (maparrowpose == null && mapwaypoint == null)  return;
	var pose = maparrowpose;
	if (maparrowpose == null) pose = mapwaypoint;

	var arrowcanvas = document.getElementById("rosmaparrow");
	var img = document.getElementById("rosmapimg");
//	arrowcanvas.width = img.naturalWidth * mapzoom;
//	arrowcanvas.height = img.naturalHeight * mapzoom;
//	arrowcanvas.style.left = mapimgleft+"px";
//	arrowcanvas.style.top = mapimgtop+"px";
	
	arrowcanvas.width = mapimgdivwidth;
	arrowcanvas.height = mapimgdivheight;
	
	var context = arrowcanvas.getContext('2d');
	context.translate(pose[0]*mapzoom + mapimgleft, pose[1]*mapzoom + mapimgtop);
	
	var linewidth = 3;
	if (rosmaparrowmode == "position") {
		var stroke = "#ffffff";
		var fill = "#000000";
	}
	else {
		var stroke = "#ffffff";
		var fill = "#ff0000";
	}
	
	var r = 10;
	if (pose[2] != null) r = 5;
	
	// circle
	context.beginPath();
	context.arc(0, 0, r, 0, 2 * Math.PI, false);
	context.fillStyle = fill;
	context.fill();
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	
	if (pose[2] == null) return;
	
	context.rotate(pose[2]);
	
	// arrow
	context.beginPath();
	context.moveTo(r, 0);
	context.lineTo(r + 30, 0);
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	context.beginPath();
	context.moveTo(r + 24,6);
	context.lineTo(r + 30,0);
	context.lineTo(r + 24,-6);
	context.lineWidth = linewidth;
	context.strokeStyle = stroke;
	context.stroke();
	
}

function torosmeters(str) {
	var x= parseFloat(str[0]);
	var y= parseFloat(str[1]);
	var th = parseFloat(str[2]);
	var res = parseFloat(mapinfo[2]);
	var originx = parseFloat(mapinfo[3]);
	var originy = parseFloat(mapinfo[4]);
	var originth = parseFloat(mapinfo[5]);
	var height = document.getElementById("rosmapimg").naturalHeight;
	
	x *= res;
	x += originx;

	y -= height;
	y *= -res;
	y += originy;
	
	th += originth;
	th *= -1;
	if (th < -Math.PI) th = Math.PI*2 + th;
//	th = Math.PI*2 -th;
	// should be: upper left = 1.5-3.14  upper right = 0-1.5  lower right = 0-(-1.5) lower left = (-1.5)-(-3.14)  
	
	return x+"_"+y+"_"+th;
}

function saverosmapwindowpos() {
	var mapwindowvalue = mapzoom+","+mapimgdivwidth+","+mapimgdivheight+","+mapimgleft+","+mapimgtop;
	createCookie("rosmapwindow", mapwindowvalue, 364 );
}

function loadrosmapwindowpos() {
	var m = readCookie("rosmapwindow");
	if (m == null) return;
	values = m.split(",");
	mapzoom = parseFloat(values[0]);
	mapimgdivwidth = parseInt(values[1]);
	mapimgdivheight = parseInt(values[2]);
	mapimgleft = parseInt(values[3]);
	mapimgtop = parseInt(values[4]);
}

function defaultrosmapwindowpos() {
	eraseCookie("rosmapwindow");
}