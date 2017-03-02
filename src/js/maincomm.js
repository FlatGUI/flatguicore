/*
 * Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

/*
 * Per-component data
 */
// TODO track removed components
var positions = [];
var viewports = [];
var clipSizes = [];
var lookVectors = [];
var childCounts = [];
var booleanStateFlags = [];
var stringPools = [];
var resourceStringPools = [];
var clientEvolvers = [];
var indicesWithClientEvolvers = [];

var paintAllSequence;

var absPositions = [];
/***/

/*
 * Image cache
 */
var componentIndexToUriWH = {}
var uriWHToImage = {}
/***/
var currentFont;
var pendingServerClipboardObject;
var lastExternalClipboardObject;
var userHasNavigatedOut = false;
var userRequestsDataExport = false;



//function resizeImage(img, w, h)
//{
//    var tmpCanvas=document.createElement("canvas");
//    tmpCanvas.width=w;
//    tmpCanvas.height=h;
//    var tmpCtx=tmpCanvas.getContext("2d");
//    tmpCtx.drawImage(img, 0, 0, w, h);
//    try
//    {
//        var resizedDataUrl = tmpCanvas.toDataURL();
//        img.src = resizedDataUrl;
//    }
//    catch(e)
//    {
//        console.log("Cannot resize " + img.src);
//        console.log(e);
//    }
//}

function getImage(index, uri) // TODO it looks like this is needed for canvas only, not for SVG
{
//    var w;
//    var h;
//    if (clipSizes[index])
//    {
//        w = clipSizes[index].w; h = clipSizes[index].h;
//    }
//    else
//    {
//        throw new Error("Unknown clipSize for component " + index);
//    }
//    var whCode = ""+w+"_"+h;
//    var requestedUriWH = uri+whCode;
    var requestedUriWH = uri;
    var existingUriWHForIndex = componentIndexToUriWH[index];
    //console.log("For " + index + " requested " + requestedUriWH);

    if (requestedUriWH != existingUriWHForIndex)
    {
        if (existingUriWHForIndex)
        {
            var usedByOtherObjects = false;
            for (var key in componentIndexToUriWH)
            {
                if (componentIndexToUriWH.hasOwnProperty(key) && key != index && componentIndexToUriWH[key] == existingUriWHForIndex)
                {
                    usedByOtherObjects = true;
                    break;
                }
            }
            if (!usedByOtherObjects)
            {
                console.log("For " + index + " deleting previous " + existingUriWHForIndex + " since it is not used by any other component");
                delete uriWHToImage[existingUriWHForIndex];
            }
        }
        componentIndexToUriWH[index] = requestedUriWH;
    }

    var img = uriWHToImage[requestedUriWH];
    if (img)
    {
        //console.log("For " + index + " found cached image for key " + requestedUriWH);
        return img;
    }
    else
    {
        //console.log("For " + index + " started loading image for key " + requestedUriWH);
        try
        {
            img = new Image;
            //img.crossOrigin="anonymous";
            img.src = uri;
            img.onload = function()
            {
                //console.log("Loaded " + uri + " " + img.width + " " + img.height);
//                if (img.width > w || img.height > h)
//                {
//                    resizeImage(img, w, h);
//                }
                repaintWholeCache();
            }
            uriWHToImage[requestedUriWH] = img;
        }
        catch (e)
        {
            console.log(e);
        }
        return img;
    }


//    var img = uriToImage[uri];
//    if (img)
//    {
//        return img;
//    }
//    else
//    {
//        try
//        {
//            img = new Image;
//            img.src = uri;
//            img.onload = function(){console.log("Loaded " + uri + " " + img.width + " " + img.height); repaintWholeCache();}
//            uriToImage[uri] = img;
//        }
//        catch (e)
//        {
//            console.log(e);
//        }
//        return img;
//    }
}

/**
 * Utilities
 */

function componentToHex(c)
{
    var hex = c.toString(16);
    return hex.length == 1 ? "0" + hex : hex;
}

function rgbToHex(codeObj)
{
    return "#" + componentToHex(codeObj.r) + componentToHex(codeObj.g) + componentToHex(codeObj.b);
}

var HOST_RESIZE_EVENT_CODE = 406;
function encodeHostSize(w, h)
{
    var bytearray = new Uint8Array(5);

    bytearray[0] = HOST_RESIZE_EVENT_CODE - 400;
    bytearray[1] = w & 0xFF
    bytearray[2] = ((w & 0xFF00) >> 8)
    bytearray[3] = h & 0xFF
    bytearray[4] = ((h & 0xFF00) >> 8)

    return bytearray.buffer;
}

var METRICS_INPUT_CODE = 407;
function sendCurrentFontMetricsToSever()
{
    var fl = currentFont ? currentFont.length : 0;

    var bytearray = new Uint8Array(1+1+fl+224);
    bytearray[0] = METRICS_INPUT_CODE-400;
    bytearray[1] = fl;
    for (var i=0; i<fl; i++)
    {
        bytearray[1+1+i] = currentFont.charCodeAt(i);
    }
    for (var c=32; c<256; c++)
    {
        var s = String.fromCharCode(c);
        bytearray[1+1+fl+c-32] = measureTextImpl(s);
    }
    webSocket.send(bytearray);
    console.log("Sent font metrics to server: " + currentFont);
}

//// //// //// ////

var POSITION_MATRIX_MAP_COMMAND_CODE = 0;
var VIEWPORT_MATRIX_MAP_COMMAND_CODE = 1;
var CLIP_SIZE_MAP_COMMAND_CODE = 2;
var LOOK_VECTOR_MAP_COMMAND_CODE = 3;
var CHILD_COUNT_MAP_COMMAND_CODE = 4;
var BOOLEAN_STATE_FLAGS_COMMAND_CODE = 5;
var STRING_POOL_MAP_COMMAND_CODE = 7;
var RESOURCE_STRING_POOL_MAP_COMMAND_CODE = 8;
var CLIENT_EVOLVER_MAP_COMMAND_CODE = 9;

var PAINT_ALL_LIST_COMMAND_CODE = 64;
var REPAINT_CACHED_COMMAND_CODE = 65;
var SET_CURSOR_COMMAND_CODE = 66;
var PUSH_TEXT_TO_CLIPBOARD = 67;
var PUSH_TEXT_TO_CLIPBOARD = 67;
var TEXT_SELECTION_MODEL_COMMAND_CODE = 68;

var TRANSMISSION_MODE_FIRST = 100;
var TRANSMISSION_MODE_LAST = 107;
var FINISH_PREDICTION_TRANSMISSION = TRANSMISSION_MODE_FIRST;
var MOUSE_LEFT_DOWN_PREDICTION = 101;
var MOUSE_LEFT_UP_PREDICTION = 102;
var MOUSE_LEFT_CLICK_PREDICTION = 103;
var MOUSE_MOVE_OR_DRAG_PREDICTION_HEADER = 104;
var MOUSE_MOVE_OR_DRAG_PREDICTION = 105;
var PING_RESPONSE = 106;
var METRICS_REQUEST = 107;

var CURSORS_BY_CODE = [
  "alias",
  "all-scroll",
  "auto",
  "cell",
  "context-menu",
  "col-resize",
  "copy",
  "crosshair",
  "default",
  "e-resize",
  "ew-resize",
  "help",
  "move",
  "n-resize",
  "ne-resize",
  "nw-resize",
  "ns-resize",
  "no-drop",
  "none",
  "not-allowed",
  "pointer",
  "progress",
  "row-resize",
  "s-resize",
  "se-resize",
  "sw-resize",
  "text",
  "vertical-text",
  "w-resize",
  "wait",
  "zoom-in",
  "zoom-out"
];

var STATE_FLAGS_VISIBILITY_MASK = 1;
var STATE_FLAGS_POPUP_MASK = 2;
var STATE_FLAGS_ROLLOVER_DISABLED_MASK = 4;
var STATE_FLAGS_PRECISE_TEXT_MEASUREMENT = 8;

// 2 bytes
function readShort(stream, c)
{
    var n = stream[c] + (stream[c+1] << 8);
    return n;
}

// 6 bytes
function readBigRect(stream, c)
{
    var w = stream[c]   + (stream[c+1] << 8) + (stream[c+2] << 16);
    var h = stream[c+3] + (stream[c+4] << 8) + (stream[c+5] << 16);

    return {w: w, h: h}
}

// 6 bytes
function readBigRectInv(stream, c)
{
    var w = stream[c]   + (stream[c+1] << 8) + (stream[c+2] << 16);
    var h = stream[c+3] + (stream[c+4] << 8) + (stream[c+5] << 16);

    return {w: -w, h: -h}
}

// 3 bytes
function readClipRect(stream, c)
{
    var w = stream[c] + ((stream[c+2] & 0x0F) << 8);
    var h = stream[c+1] + ((stream[c+2] & 0xF0) << 4);

    return {x: 0, y: 0, w: w, h: h}
}

function checkFlagForComponent(index, mask)
{
    return ((booleanStateFlags[index] & mask) === mask);
}


// Note prefetch is implemented for images only.
// Need generic way which should eventually become available.
function decodeStringPool(stream, c, byteLength, poolMatrix, prefetchResource)
{
    while (c < byteLength)
    {
        var index = readShort(stream, c);
        c+=2;
        var sCount = stream[c];
        c++;
        for (var i=0; i<sCount; i++)
        {
            var sIndex = stream[c];
            c++;
            var sSize = readShort(stream, c);
            c+=2;
            var str = "";
            for (var j=0; j<sSize; j++)
            {
                str += String.fromCharCode(stream[c+j]);
            }
            c+=sSize;

            if (prefetchResource)
            {
                if (clipSizes[index])
                {
                    console.log("Prefetching: " + str + " for component " + index + " of size " + clipSizes[index].w + ";" + clipSizes[index].h);
                    getImage(index, str);
                }
                else
                {
                    console.log("Skipped prefetching " + str + " for component " + index + " because of unknown size");
                }
            }

            if (!poolMatrix[index])
            {
                poolMatrix[index] = [];
            }
            poolMatrix[index][sIndex] = str;
        }
    }
    return c;
}

function decodeCommandVector(stream, byteLength)
{
    if (byteLength == 0)
    {
        return;
    }

    var codeObj;
    var opcodeBase = stream[0];
    var c = 1;

    switch (opcodeBase)
    {
        case POSITION_MATRIX_MAP_COMMAND_CODE:
            decodeCVLog("Position mx map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var position = readBigRect(stream, c);
                c+=6;
                positions[index] = position;
                processPositionMatrixUpdate(index);
            }
            break;
        case VIEWPORT_MATRIX_MAP_COMMAND_CODE:
            decodeCVLog("Viewport mx map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var viewport = readBigRectInv(stream, c);
                c+=6;
                viewports[index] = viewport;
                processViewportMatrixUpdate(index);
            }
            break;
        case CLIP_SIZE_MAP_COMMAND_CODE:
            decodeCVLog("Clip size mx map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var clipSize = readClipRect(stream, c);
                c+=3;
                clipSizes[index] = clipSize;
                processClipSizeUpdate(index);
            }
            break;
        case LOOK_VECTOR_MAP_COMMAND_CODE:
            decodeCVLog("Look vector map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var lookVectorSize = readShort(stream, c);
                c+=2;

                var lookVector = [];
                for (i=0; i<lookVectorSize; i++)
                {
                    lookVector[i] = stream[c+i];
                }
                c += lookVectorSize;
                lookVectors[index] = lookVector;
                processLookVectorUpdate(index);
            }
            break;
        case CHILD_COUNT_MAP_COMMAND_CODE:
            decodeCVLog("Child count map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var childCount = readShort(stream, c);
                c+=2;
                childCounts[index] = childCount;
            }
            processChildCountMap()
            break;
        case BOOLEAN_STATE_FLAGS_COMMAND_CODE:
            decodeCVLog("State flags map");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                booleanStateFlags[index] = stream[c];
                c++;
                processBooleanFlagMap(index);
            }
            break;
        case STRING_POOL_MAP_COMMAND_CODE:
            decodeCVLog("Str pool map");
            c = decodeStringPool(stream, c, byteLength, stringPools, false);
            break;
        case RESOURCE_STRING_POOL_MAP_COMMAND_CODE:
            decodeCVLog("Res str pool map");
            c = decodeStringPool(stream, c, byteLength, resourceStringPools, true);
            break;
        case CLIENT_EVOLVER_MAP_COMMAND_CODE:
            decodeCVLog("Client evolver map -- DEPRECATED");
            while (c < byteLength)
            {
                var index = readShort(stream, c);
                c+=2;
                var strLen = readShort(stream, c);
                c+=2;
                var s = "";
                for (var i = 0; i<strLen; i++)
                {
                    s += String.fromCharCode(stream[c+i]);
                }
                eval("var r = " + s);
                clientEvolvers[index] = r;
                indicesWithClientEvolvers.push(index);
                console.log("Received client evolver " + index + " : " + s + "/" + JSON.stringify(clientEvolvers[index]) + " in=" + indicesWithClientEvolvers);
                c+=strLen;
            }
            break;
        // TODO for SVG, need to accept paintAllList only once. Then need to track added/removed children
        // TODO same may be implemented for canvas. It will optimize the amounts of data transferred by Canvas impl and make Canvas/SVG impls closer
        case PAINT_ALL_LIST_COMMAND_CODE:
            decodeCVLog("Paint all list");
            paintAllSequence = stream;

            var cc = c
            var pas = '';
            while(cc < byteLength)
            {
                var cci = readShort(stream, cc);
                cc+=2;
                pas += cci+',';
            }
            decodeCVLog(pas);

            c = processPaintAllList(paintAllSequence);
//            while (c < byteLength)
//            {
//                c = paintComponent(stream, c);
//            }
            break;
        case REPAINT_CACHED_COMMAND_CODE:
            decodeCVLog("Repaint cached");
            repaintWholeCache();
            break;
        case SET_CURSOR_COMMAND_CODE:
            decodeCVLog("Set cursor");
            var cursorCode = stream[c];
            c++;
            canvas.style.cursor = CURSORS_BY_CODE[cursorCode];
            break;
        case PUSH_TEXT_TO_CLIPBOARD:
            decodeCVLog("Push text to clipboard");
            // TODO why do we transmit size - isn't byteLength (used for decoding paint all list) enough?
            var sSize = readShort(stream, c);
            c+=2;
            var str = "";
            for (var j=0; j<sSize; j++)
            {
                str += String.fromCharCode(stream[c+j]);
            }
            c+=sSize;
            pendingServerClipboardObject = str;
            if (userRequestsDataExport)
            {
                window.prompt("Copy to clipboard: Ctrl+C, Enter", pendingServerClipboardObject);
                userRequestsDataExport = false;
            }
            break;
        case TEXT_SELECTION_MODEL_COMMAND_CODE:
            if (c >= byteLength)
            {
                console.log("SELECT: received empty selection")
                applyTextSelection(null, "");
                break;
            }
            var index = readShort(stream, c);
            c+=2;
            var selStartLinePoolId = readShort(stream, c);
            c+=2;
            var selStartLinePos = readShort(stream, c);
            c+=2;
            var selStr = "";
            if (byteLength == 1+2+2+2+2)
            {
                // Single line selection
                var endStartLinePos = readShort(stream, c);
                c+=2;
                selStr = stringPools[index][selStartLinePoolId].substring(selStartLinePos,endStartLinePos);
                //console.log("Text single selection model for " + index + ": " + selStartLinePoolId + "-" + selStartLinePos + "-" + selStartLinePoolId + "-" + endStartLinePos);
                var absPos = getComponentAbsPosition(index);
                console.log("SELECT: s " + selStr + " x=" + absPos.x + " y=" + absPos.y);
            }
            else
            {
                // Multi line selection
                var endStartLinePoolId = readShort(stream, c);
                c+=2;
                var endStartLinePos = readShort(stream, c);
                c+=2;
                selStr += stringPools[index][selStartLinePoolId].substring(selStartLinePos);
                //decodeCVLog("Text selection model for " + index + ": " + selStartLinePoolId + "-" + selStartLinePos + "-" + endStartLinePoolId + "-" + endStartLinePos);
                //console.log("Text multiselection model for " + index + ": " + selStartLinePoolId + "-" + selStartLinePos + "-" + endStartLinePoolId + "-" + endStartLinePos);
                while(c < byteLength)
                {
                    var si = readShort(stream, c);
                    c+=2;
                    selStr += stringPools[index][si];
                }
                selStr += stringPools[index][endStartLinePoolId].substring(0,endStartLinePos);
                console.log("SELECT: m " + selStr)
            }
            applyTextSelection(index, selStr);
            break;
        default:
           throw new Error("Unknown command code: " + stream[0]);
    }
}

var TEXT_SEL_ID = "textSel";
function applyTextSelection(index, selStr)
{
    var d = document.getElementById(TEXT_SEL_ID);
    var t;
    if (d == null)
    {
        d = document.createElement("div")
        d.id = TEXT_SEL_ID
        d.style.position = 'absolute'
        d.style.opacity = '0'
        d.onclick = function(evt){if (evt.button != 2) {this.style.width=0;this.style.height=0}};
        t = document.createTextNode(selStr);
        d.appendChild(t);
        document.body.appendChild(d);
    }
    else
    {
        t = d.childNodes[0];
        t.nodeValue = selStr;
    }

    if (index)
    {
        var absPos = getComponentAbsPosition(index);
        console.log("APLSELECT: s " + selStr + " x=" + absPos.x + " y=" + absPos.y);
        d.style.left = absPos.x + 'px';
        d.style.top = absPos.y + 'px';
        d.style.width = clipSizes[index].w + 'px';
        d.style.height = clipSizes[index].h + 'px';
    }

    var r = document.createRange();
    var s = window.getSelection();
    var range = document.createRange();
    range.setStart(t, 0);
    range.setEnd(t, selStr.length);
    s.removeAllRanges();
    s.addRange(range);
}

var messages = document.getElementById("messages");

var lastMouseX = -1;
var lastMouseY = -1;

/**
 * WebSocket
 */

var webSocket;
var connectionOpen;

var transmissionMode = FINISH_PREDICTION_TRANSMISSION;

var lastPingTime;
var roundTripTests;
var avgRoundTripTime;

var mouseDownPredictionDatas = [];
var mouseDownPredictionDataSizes = [];
var mouseDownPredictionCounter = 0;

var mouseUpPredictionDatas = [];
var mouseUpPredictionDataSizes = [];
var mouseUpPredictionCounter = 0;

var mouseClickPredictionDatas = [];
var mouseClickPredictionDataSizes = [];
var mouseClickPredictionCounter = 0;

var mouseMovePredictionsPerPoint = 0;
var mouseMovePredictionX = [];
var mouseMovePredictionY = [];
var mouseMovePredictionBufIndices = [];
var mouseMovePredictionBufCount = 0;
var mouseMovePredictionBufCounter = 0;
var mouseMovePredictionBufs = [];
var mouseMovePredictionBufSizes = [];

var mouseIntervalMillis = 50;

var PING_INPUT_CODE = 408;

function sendPingToSever()
{
    var bytearray = new Uint8Array(1);
    bytearray[0] = PING_INPUT_CODE-400;
    webSocket.send(bytearray);
    roundTripTests++;
    var now = Date.now();
    if (lastPingTime && roundTripTests > 3)
    {
        var roundTripTime = now - lastPingTime;
        if (avgRoundTripTime) avgRoundTripTime = (avgRoundTripTime + roundTripTime) / 2; else avgRoundTripTime = roundTripTime;
        mouseIntervalMillis = avgRoundTripTime / 4;
        if (mouseIntervalMillis < 7) mouseIntervalMillis = 7;
        //console.log("Roundtrip test #" + roundTripTests + " " + roundTripTime + " avg=" + avgRoundTripTime);
    }
    lastPingTime = now;
}
function measureConnection()
{
    lastPingTime = null;
    avgRoundTripTime = null;
    roundTripTests = 0
    sendPingToSever();
}

var tryAlternativeServers=true;
var serverAttempt=0;
function openSocket()
{
    displayUserTextMessage("Establishing connection...", 10, 20);
    displayStatus("establishing...");

    if(webSocket !== undefined && webSocket.readyState !== WebSocket.CLOSED)
    {
        return;
    }

    serverWebSocketUri = serverWebSocketUris[serverAttempt];

    var msg = "Trying "+serverWebSocketUri+" (server #"+serverAttempt+" of "+serverWebSocketUris.length+")";
    console.log(msg);

    webSocket = new WebSocket(serverWebSocketUri);

    webSocket.binaryType = "arraybuffer";

    webSocket.onopen = function(event)
    {
        connectionOpen = true;
        tryAlternativeServers=false;// If re-connect then only to where it was
        displayUserTextMessage("Open.", 10, 30);
        displayStatus("open");

        handleResize(null);

        measureConnection();
    };

    webSocket.onmessage = function(event)
    {
        if (event.data.byteLength)
        {
            if (connectionOpen)
            {
                //var time = Date.now();
                //console.log("Received responce " + time);

                var dataBuffer = new Uint8Array(event.data);

                if (event.data.byteLength > 0 && dataBuffer[0] >= TRANSMISSION_MODE_FIRST && dataBuffer[0] <= TRANSMISSION_MODE_LAST)
                {
                    transmissionMode = dataBuffer[0];
                    switch (transmissionMode)
                    {
                        case MOUSE_LEFT_DOWN_PREDICTION:
                             mouseDownPredictionCounter = 0;
                             break;
                        case MOUSE_LEFT_UP_PREDICTION:
                             mouseUpPredictionCounter = 0;
                             break;
                        case MOUSE_LEFT_CLICK_PREDICTION:
                             mouseClickPredictionCounter = 0;
                             break;
                        case MOUSE_MOVE_OR_DRAG_PREDICTION_HEADER:
                             var c = 1;
                             mouseMovePredictionsPerPoint = dataBuffer[c];
                             c++;
                             for (var pnt=0; pnt<mouseMovePredictionsPerPoint; pnt++)
                             {
                                var deltas = dataBuffer[c];
                                mouseMovePredictionX[pnt] = lastMouseX + (deltas & MASK_LB) - 8;
                                mouseMovePredictionY[pnt] = lastMouseY + ((deltas & MASK_HB) >> 4) - 8;
                                c++;
                                mouseMovePredictionBufIndices[pnt] = dataBuffer[c];
                                c++;
                             }
                             mouseMovePredictionBufCount = dataBuffer[c];
                             transmissionMode = MOUSE_MOVE_OR_DRAG_PREDICTION;
                             break;
                        case PING_RESPONSE:
                             if (roundTripTests < 24) sendPingToSever();
                             transmissionMode = FINISH_PREDICTION_TRANSMISSION;
                             break;
                        case METRICS_REQUEST:
                             var c = 1;
                             var sSize = readShort(dataBuffer, c);
                             c+=2;
                             var str = "";
                             for (var j=0; j<sSize; j++)
                             {
                                str += String.fromCharCode(dataBuffer[c+j]);
                             }
                             console.log("Received initial metrics request for: " + str);
                             c+=sSize;
                             currentFont = str;
                             applyCurrentFont();
                             sendCurrentFontMetricsToSever();
                    }
                }
                else
                {
                    if (transmissionMode == FINISH_PREDICTION_TRANSMISSION)
                    {
                        decodeCommandVector(dataBuffer, event.data.byteLength);
                    }
                    else
                    {
                        switch (transmissionMode)
                        {
                            case MOUSE_LEFT_DOWN_PREDICTION:
                                 mouseDownPredictionDatas[mouseDownPredictionCounter] = dataBuffer;
                                 mouseDownPredictionDataSizes[mouseDownPredictionCounter] = event.data.byteLength;
                                 mouseDownPredictionCounter++;
                                 //console.log("Received predictions for mouse down: " + event.data.byteLength);
                                 break;
                            case MOUSE_LEFT_UP_PREDICTION:
                                 mouseUpPredictionDatas[mouseUpPredictionCounter] = dataBuffer;
                                 mouseUpPredictionDataSizes[mouseUpPredictionCounter] = event.data.byteLength;
                                 mouseUpPredictionCounter++;
                                 //console.log("Received predictions for mouse up: " + event.data.byteLength);
                                 break;
                            case MOUSE_LEFT_CLICK_PREDICTION:
                                 mouseClickPredictionDatas[mouseClickPredictionCounter] = dataBuffer;
                                 mouseClickPredictionDataSizes[mouseClickPredictionCounter] = event.data.byteLength;
                                 mouseClickPredictionCounter++;
                                 //console.log("Received predictions for mouse click: " + event.data.byteLength);
                                 break;
                            case MOUSE_MOVE_OR_DRAG_PREDICTION:
                                 mouseMovePredictionBufs[mouseMovePredictionBufCounter] = dataBuffer;
                                 mouseMovePredictionBufSizes[mouseMovePredictionBufCounter] = event.data.byteLength;
                                 mouseMovePredictionBufCounter++;
                                 break;
                        }
                    }
                }

                //time = Date.now();
                //console.log("Rendered responce " + time + " cmd =" + dataBuffer[0]);
            }
            else
            {
                displayUserTextMessage("Connection to remote server is closed. Please reload.", 10, 50);
                displayStatus("closed");
            }
        }
        else
        {
            displayUserTextMessage(event.data, 10, 30);
        }
    };

    webSocket.onclose = function(event)
    {
        displayUserTextMessage("Connection to remote server is closed. Please reload.", 10, 50);
        displayStatus("closed");
        connectionOpen = false;
    };

    webSocket.onerror = function(event)
    {
        var msg = "Could not connect to "+serverWebSocketUri+" (server #"+serverAttempt+" of "+serverWebSocketUris.length+")";
        console.log(msg);

        if (tryAlternativeServers && serverAttempt < serverWebSocketUris.length-1)
        {
            serverAttempt++;
            openSocket();
        }
        else
        {
            if (tryAlternativeServers)
            {
                console.log("Fatal error: non of "+serverWebSocketUris.length+" servers responded.");
            }
            displayUserTextMessage(msg, 10, 70);
            displayStatus("error, closed");
            connectionOpen = false;
        }
    };
}

//// //// //// ////

/**
 * Input events
 */

function sendEventToServer(evt)
{
    //var time = Date.now();
    //console.log("Before sending " + time);

    if (connectionOpen)
    {
        webSocket.send(evt);
    }

    //time = Date.now();
    //console.log("After sending " + time);
}

var mouseDown = false;
var lastMouseDragTime = 0;
var lastUnprocessedMouseDrag;
var lastUnprocessedMouseMove;
var lastIndexUnderMouse = -1;
var lastMousePosWasOnEdge = false;

function getEncodedMouseEvent(x, y, id)
{
    var bytearray = new Uint8Array(4);

    bytearray[0] = id - 400;
    bytearray[1] = x & 0xFF;
    bytearray[2] = y & 0xFF;
    bytearray[3] = ((x >> 4) & 0xF0) | ((y >> 8) & 0x0F);

    return bytearray.buffer;
}

function storeMouseEventAndGetEncoded(evt, id)
{
    var rect = getHostBoundingClientRect();
    var x = evt.clientX - rect.left;
    var y = evt.clientY - rect.top;

    lastMouseX = x;
    lastMouseY = y;

    return getEncodedMouseEvent(x, y, id);
}

function sendMouseDownEventToServer(evt)
{
    if(evt.preventDefault) evt.preventDefault();
    if(evt.stopPropagation) evt.stopPropagation();
    //evt.cancelBubble=true;
    //evt.returnValue=false;

    if (transmissionMode == FINISH_PREDICTION_TRANSMISSION && mouseDownPredictionCounter > 0)
    {
        //console.log("mouse down - hit prediction (" + mouseDownPredictionCounter + " predictions)");
        for (var i=0; i<mouseDownPredictionCounter; i++)
        {
            decodeCommandVector(mouseDownPredictionDatas[i], mouseDownPredictionDataSizes[i]);
        }
        mouseDownPredictionDatas = [];
        mouseDownPredictionDataSizes = [];
        mouseDownPredictionCounter = 0;
    }
    mouseDown = true;
    sendEventToServer(storeMouseEventAndGetEncoded(evt, 501));
}

function commitLastUnprocessedMouseMove()
{
    if (lastUnprocessedMouseMove)
    {
        sendEventToServer(storeMouseEventAndGetEncoded(lastUnprocessedMouseMove, 503));
        lastUnprocessedMouseMove = null;
    }
}

function commitLastUnprocessedMouseDrag()
{
    if (lastUnprocessedMouseDrag)
    {
        sendEventToServer(storeMouseEventAndGetEncoded(lastUnprocessedMouseDrag, 506));
        lastUnprocessedMouseDrag = null;
    }
}

function commitPendingMouseEvents()
{
    commitLastUnprocessedMouseMove();
    commitLastUnprocessedMouseDrag();
}

function sendMouseUpEventToServer(evt)
{
    if(evt.preventDefault) evt.preventDefault();
    if(evt.stopPropagation) evt.stopPropagation();

    if (transmissionMode == FINISH_PREDICTION_TRANSMISSION && mouseUpPredictionCounter > 0)
    {
        //console.log("mouse Up - hit prediction (" + mouseUpPredictionCounter + " predictions)");
        for (var i=0; i<mouseUpPredictionCounter; i++)
        {
            decodeCommandVector(mouseUpPredictionDatas[i], mouseUpPredictionDataSizes[i]);
        }
        mouseUpPredictionDatas = [];
        mouseUpPredictionDataSizes = [];
        mouseUpPredictionCounter = 0;
    }
    commitLastUnprocessedMouseDrag();
    mouseDown = false;
    sendEventToServer(storeMouseEventAndGetEncoded(evt, 502));
}

function sendMouseClickEventToServer(evt)
{
    if(evt.preventDefault) evt.preventDefault();
    if(evt.stopPropagation) evt.stopPropagation();
    //evt.cancelBubble=true;
    //evt.returnValue=false;

    if (transmissionMode == FINISH_PREDICTION_TRANSMISSION && mouseClickPredictionCounter > 0)
    {
        //console.log("mouse Click - hit prediction (" + mouseClickPredictionCounter + " predictions)");
        for (var i=0; i<mouseClickPredictionCounter; i++)
        {
            decodeCommandVector(mouseClickPredictionDatas[i], mouseClickPredictionDataSizes[i]);
        }
        mouseClickPredictionDatas = [];
        mouseClickPredictionDataSizes = [];
        mouseClickPredictionCounter = 0;
    }
    sendEventToServer(storeMouseEventAndGetEncoded(evt, 500));
}

function sendMouseMoveEventToServer(evt)
{
    if(evt.preventDefault) evt.preventDefault();
    if(evt.stopPropagation) evt.stopPropagation();
    //evt.cancelBubble=true;
    //evt.returnValue=false;

    var rect = getHostBoundingClientRect();
    var x = evt.clientX - rect.left;
    var y = evt.clientY - rect.top;

    if ( x != lastMouseX || y != lastMouseY)
    {
        // Any click predictions are not valid any more
        mouseDownPredictionDatas = [];
        mouseDownPredictionDataSizes = [];
        mouseDownPredictionCounter = 0;
        mouseUpPredictionDatas = [];
        mouseUpPredictionDataSizes = [];
        mouseUpPredictionCounter = 0;
        mouseClickPredictionDatas = [];
        mouseClickPredictionDataSizes = [];
        mouseClickPredictionCounter = 0;

        // See if this point is already predicted
        for (var i=0; i<mouseMovePredictionsPerPoint; i++)
        {
            if (mouseMovePredictionX[i] == x && mouseMovePredictionY[i] == y)
            {
                var bufIndex = mouseMovePredictionBufIndices[i];
                if (mouseMovePredictionBufs[bufIndex] && mouseMovePredictionBufSizes[bufIndex])//TODO Not clear why no buffer sometimes
                {
                    decodeCommandVector(mouseMovePredictionBufs[bufIndex], mouseMovePredictionBufSizes[bufIndex]);
                    mouseMovePredictionsPerPoint = 0;
                    mouseMovePredictionY = [];
                    mouseMovePredictionY = [];
                    mouseMovePredictionBufIndices = [];
                    mouseMovePredictionBufCount = 0;
                    mouseMovePredictionBufCounter = 0;
                    mouseMovePredictionBufs = [];
                    mouseMovePredictionBufSizes = [];
                    console.log("Used prediction for move " + x + " " + y);
                    return;
                }
            }
        }

        if (mouseDown)
        {
            var nowTime = Date.now();
            if (nowTime - lastMouseDragTime > mouseIntervalMillis)
            {
                lastUnprocessedMouseDrag = null;
                sendEventToServer(storeMouseEventAndGetEncoded(evt, 506));
            }
            else
            {
                lastUnprocessedMouseDrag = evt;
            }
            lastMouseDragTime = nowTime;
        }
        else
        {
            var indexUnderMouse = getIndexUnderMouse(x,y);
            var onEdge = false;

            if (!onEdge && lastIndexUnderMouse && /*isComponentReadyForMouseRollover(lastIndexUnderMouse)*/absPositions[lastIndexUnderMouse])
            {
                var i = lastIndexUnderMouse;
                var ix = absPositions[i][0][2];
                var iy = absPositions[i][1][2];
                onEdge = t(x, ix) || t(y, iy) || t(x, ix+clipSizes[i].w-1) || t(y, iy+clipSizes[i].h-1);
            }

            if (indexUnderMouse !== lastIndexUnderMouse || lastMousePosWasOnEdge || onEdge)
            {
                sendEventToServer(getEncodedMouseEvent(lastMouseX, lastMouseY, 503));
                sendEventToServer(storeMouseEventAndGetEncoded(evt, 503));

                lastIndexUnderMouse = indexUnderMouse;
                lastMousePosWasOnEdge = onEdge;
            }
            else
            {
                lastUnprocessedMouseMove = evt;
            }
        }
    }
}

window.setInterval(commitPendingMouseEvents, mouseIntervalMillis * 5);
window.setInterval(measureConnection, 60000);

var CLIPBOARD_PASTE_EVENT_CODE = 403;
var CLIPBOARD_COPY_EVENT_CODE = 404;

function handlePaste(evt)
{
    var text;

    var eData = evt.clipboardData.getData('text/plain');

    if (evt && evt.clipboardData && evt.clipboardData.getData)
    {
        if (pendingServerClipboardObject)
        {
            if (userHasNavigatedOut && eData != lastExternalClipboardObject)
            {
                // External clipboard content has changed when user navigated out of the window and then back
                lastExternalClipboardObject = eData;
                text = eData;
                pendingServerClipboardObject = null;
            }
            else
            {
                text = pendingServerClipboardObject;
                lastExternalClipboardObject = eData;
            }
        }
        else
        {
            text = eData;
        }
    }
    else
    {
        text = pendingServerClipboardObject;
    }

    // Just decided which text to paste, so this flag is not needed any more
    userHasNavigatedOut = false;

    if (text && text.length)
    {
        var bytearray = new Uint8Array(3 + text.length);

        bytearray[0] = CLIPBOARD_PASTE_EVENT_CODE - 400;
        bytearray[1] = text.length & 0xFF;
        bytearray[2] = ((text.length & 0xFF00) >> 8);

        for (var i=0; i<text.length; i++)
        {
            bytearray[3+i] = text.charCodeAt(i);
        }

        sendEventToServer(bytearray.buffer);
    }
}

function handleCopyEvent()
{
    // Here we don't know what has to be copied yet. We just send copy event to server.
    var bytearray = new Uint8Array(1);
    bytearray[0] = CLIPBOARD_COPY_EVENT_CODE - 400;
    sendEventToServer(bytearray.buffer);
}

window.addEventListener("focus", function(e){userHasNavigatedOut=true;}, false);

window.addEventListener("paste", handlePaste, false);
window.addEventListener("copy", handleCopyEvent, false);

function getEncodedKeyEvent(evt, id)
{
    var bytearray = new Uint8Array(5);

    bytearray[0] = id - 400;
    bytearray[1] = evt.keyCode & 0xFF
    bytearray[2] = ((evt.keyCode & 0xFF00) >> 8)
    bytearray[3] = evt.charCode & 0xFF
    bytearray[4] = ((evt.charCode & 0xFF00) >> 8)

    return bytearray.buffer;
}

function sendKeyDownEventToServer(evt)
{
    sendEventToServer(getEncodedKeyEvent(evt, 401));
    // Do not let browser process TAB, Backspace, Home, End, Space, arrows
    if (evt.preventDefault && (evt.keyCode == 9 || evt.keyCode == 8 || evt.keyCode == 36 || evt.keyCode == 35 ||
        evt.keyCode == 37 || evt.keyCode == 38 || evt.keyCode == 39 || evt.keyCode == 40))
    {
        evt.preventDefault();
        evt.stopPropagation();
    }
}

function handleKeyDownEvent(evt)
{
    // Special handling for CTRL+ALT+SHIFT+C: give user chance to copy text to system clipboard
    if(evt.shiftKey && evt.altKey && evt.ctrlKey && evt.keyCode == 67)
    {
        userRequestsDataExport = true;
        handleCopyEvent();
    }
    sendKeyDownEventToServer(evt);
}

function sendKeyUpEventToServer(evt)
{
//    if (evt.keyCode == 9 && evt.ctrlKey) // This may be used for emulating Ctrl+Tab
//    {
//        sendEventToServer(getEncodedKeyEvent(evt, 401));
//    }
    sendEventToServer(getEncodedKeyEvent(evt, 402));
    if (evt.preventDefault && (evt.keyCode == 9 || evt.keyCode == 8 || evt.keyCode == 36 || evt.keyCode == 35)) // Do not let browser process TAB, Backspace, Home, End
    {
        evt.preventDefault();
        evt.stopPropagation();
    }
}

function sendKeyPressEventToServer(evt)
{
    sendEventToServer(getEncodedKeyEvent(evt, 400));
}

window.addEventListener("keydown", handleKeyDownEvent, false);
window.addEventListener("keyup", sendKeyUpEventToServer, false);
window.addEventListener("keypress", sendKeyPressEventToServer, false);

// Remove scroll bars
document.documentElement.style.overflow = 'hidden';  // firefox, chrome
document.body.scroll = "no"; // ie only