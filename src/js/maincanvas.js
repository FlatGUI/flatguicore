/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

var referenceFont = '12px Tahoma';
var canvas = document.getElementById("hostCanvas");
var ctx = canvas.getContext("2d");
function initCanvas()
{
    ctx.lineWidth = 1;
    ctx.font = referenceFont;
}
initCanvas();

function adjustCanvasSize()
{
    canvas.width  = window.innerWidth;
    canvas.height = window.innerHeight;
    initCanvas();
}

function handleResize(evt)
{
    adjustCanvasSize();
    sendEventToServer(getEncodedHostResizeEvent());
}

window.onresize = handleResize;


var currentTransform = [[1, 0, 0],
                        [0, 1, 1],
                        [0, 0, 1]];
var currentClip = {x: 0, y: 0, w: Infinity, h: Infinity};
var clipRectStack = [];


function mxMult(m1, m2)
{
    var result = [];
    for (var i = 0; i < m1.length; i++)
    {
        result[i] = [];
        for (var j = 0; j < m2[0].length; j++)
        {
            var sum = 0;
            for (var k = 0; k < m1[0].length; k++)
            {
                sum += m1[i][k] * m2[k][j];
            }
           result[i][j] = sum;
        }
   }
   return result;
}

function translatonInverse(m)
{
    return [[1, 0, 0, -m[0][3]]
            [0, 1, 0, -m[1][3]]
            [0, 0, 1, -m[2][3]]
            [0, 0, 0,       1]];
}

function lineInt(a1, a2, b1, b2)
{
    if (b1 >= a1 && a2 > b1 && b2 >= a2)
    {
        return {a: b1, b: a2}
    }
    else if (a1 >= b1 && b2 > a1 && a2 >= b2)
    {
        return {a: a1, b: b2}
    }
    else if (b1 <= a1 && a1 <= a2 && a2 <= b2)
    {
        return {a: a1, b: a2}
    }
    else if (a1 <= b1 && b1 <= b2 && b2 <= a2)
    {
        return {a: b1, b: b2}
    }
    else
    {
        return null;
    }
}

function rectInt(a, b)
{
    var x1a = a.x;
    var y1a = a.y;
    var x2a = x1a + a.w;
    var y2a = y1a + a.h;
    var x1b = b.x;
    var y1b = b.y;
    var x2b = x1b + b.w;
    var y2b = y1b + b.h;
    var xinter = lineInt(x1a, x2a, x1b, x2b);
    var yinter = lineInt(y1a, y2a, y1b, y2b);

    if (xinter && yinter)
    {
        var x1 = xinter.a;
        var x2 = xinter.b;
        var y1 = yinter.a;
        var y2 = yinter.b;

        return {x: x1, y: y1, w: (x2-x1), h: (y2-y1)};
    }
    else
    {
        return null;
    }
}

/**
 * Binary decoding/performing
 */

function applyCurrentTransform()
{
    ctx.setTransform(currentTransform[0][0], currentTransform[1][0], currentTransform[0][1], currentTransform[1][1], currentTransform[0][2], currentTransform[1][2]);
}

function applyCurrentFont()
{
    if (currentFont)
    {
        ctx.font = currentFont;
    }
}

function applyCurrentClip()
{
    if (currentClip)
    {
        ctx.restore();
        ctx.save();
        applyCurrentTransform();
        applyCurrentFont();
        currentTx = currentTransform[0][2];
        currentTy = currentTransform[1][2];
        ctx.beginPath();
        ctx.rect(currentClip.x-currentTx, currentClip.y-currentTy, currentClip.w, currentClip.h);
        ctx.clip();
        ctx.closePath();
    }
    else
    {
        ctx.beginPath();
        ctx.rect(0, 0, 0, 0);
        ctx.clip();
        ctx.closePath();
    }
}

function pushCurrentClip()
{
    clipRectStack.push(currentClip);
}

function popCurrentClip()
{
    currentClip = clipRectStack.pop();
    applyCurrentClip();
}

function applyTransform(codeObj)
{
    ctx.transform(1, 0, 0, 1, codeObj.w, codeObj.h);
    currentTransform = mxMult(currentTransform, [[     1,      0, codeObj.w],
                                                 [     0,      1, codeObj.h],
                                                 [     0,      0,         1]]);
}

function applyInverseTransform(codeObj)
{
    ctx.transform(1, 0, 0, 1, -codeObj.w, -codeObj.h);
    currentTransform = mxMult(currentTransform, [[     1,      0, -codeObj.w],
                                                 [     0,      1, -codeObj.h],
                                                 [     0,      0,          1]]);
}

function clipRect(codeObj)
{
    if (currentClip)
    {
        currentTx = currentTransform[0][2];
        currentTy = currentTransform[1][2];
        codeObjT = {x: currentTx, y: currentTy, w: codeObj.w, h: codeObj.h};

        currentClip = rectInt(currentClip, codeObjT);
    }
    applyCurrentClip();
}

function setClip(codeObj)
{
    currentTx = currentTransform[0][2];
    currentTy = currentTransform[1][2];
    currentClip = {x: currentTx, y: currentTy, w: codeObj.w, h: codeObj.h};
    applyCurrentClip();
}

function getTextLineHeight() // TODO implement properly
{
    return ctx.measureText("M").width;
}

// Saves us from discrepancy between string rendering on different devices (e.g. DirectWrite vs GDI)
// This is important given the way metrics are sent to server
function fillText(s, x, y)
{
    var p=x;
    for (var i=0; i<s.length; i++)
    {
        var c = s.charAt(i);
        ctx.fillText(c, p, y);
        p += ctx.measureText(c).width;
    }
}

function fillMultilineTextNoWrap(text, x, y)
{
    var lines = text.split("\n");
    var lineHeight = getTextLineHeight();
    for (var i=0; i<lines.length; i++)
    {
        fillText(lines[i], x, y + i*1.5*lineHeight);
    }
}

function measureTextImpl(s)
{
    return ctx.measureText(s).width
}

function displayUserTextMessage(msg, x, y)
{
    ctx.beginPath();
    ctx.fillStyle="#FFFFFF";
    ctx.strokeStyle="#FFFFFF";
    ctx.fillText(msg, x, y);
    ctx.closePath();
}

function displayStatus(msg)
{
    //messages.innerHTML = "Connection status: " + msg;
}

function decodeLog(msg)
{
    //console.log(msg);
}

function decodeCVLog(msg)
{
    console.log(msg);
}

function decodeLookVector(componentIndex, stream, byteLength)
{
    var c = 0;
    while (c < byteLength)
    {
        var codeObj;

        if (stream[c] == 0) // Extended commands
        {
            var opcodeBase = stream[c+1];
            c++;

            switch (opcodeBase)
            {
                case CODE_DRAW_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    var imageUrl;
                    // This is ok, draw image command may arrive before string pool update, just need to check
                    if (resourceStringPools[componentIndex])
                    {
                        imageUrl = resourceStringPools[componentIndex][codeObj.i];
                    }
                    // This is ok, look vector update with new image may arrive before string pool update, just need to check
                    if (imageUrl)
                    {
                        var img = getImage(componentIndex, imageUrl);
                        ctx.drawImage(img, codeObj.x, codeObj.y);
                    }
                    c += codeObj.len;
                    break;
                case CODE_FIT_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    var img = getImage(componentIndex, resourceStringPools[componentIndex][codeObj.i]);
                    ctx.drawImage(img, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    c += codeObj.len;
                    break;
                case CODE_FILL_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    var img = getImage(componentIndex, resourceStringPools[componentIndex][codeObj.i]);
                    var w = img.width;
                    var h = img.height;
                    if (codeObj.w <= w && codeObj.h <= h)
                    {
                        ctx.drawImage(img, codeObj.x, codeObj.y);
                    }
                    else if (w > 0 && h >0)
                    {
                        for (var ix=0; ix<codeObj.w; ix+=w)
                        {
                            for (var iy=0; iy<codeObj.h; iy+=h)
                            {
                                ctx.drawImage(img, codeObj.x+ix, codeObj.y+iy);
                            }
                        }
                    }
                    else
                    {
                        decodeLog("Cannot fill image with zero size: w=" + w + " h=" + h);
                    }
                    c += codeObj.len;
                    break;
                case CODE_SET_FONT:
                case CODE_SET_FONT_AND_REQUEST_METRICS:
                    if (opcodeBase == CODE_SET_FONT_AND_REQUEST_METRICS)
                    {
                        console.log("Received font request from server. currentFont = " + currentFont + "; opcode = " + opcodeBase  +"; component: " + componentIndex);
                    }
                    codeObj = decodeFontStrPool(stream, c);
                    if (stringPools[componentIndex] && stringPools[componentIndex][codeObj.i])
                    {
                       currentFont = stringPools[componentIndex][codeObj.i];
                       applyCurrentFont();
                    }
                    if (opcodeBase == CODE_SET_FONT_AND_REQUEST_METRICS && currentFont)
                    {
                        sendCurrentFontMetricsToSever();
                        stream[c] = CODE_SET_FONT; // Look vector is cached, so do not repeat sending metrics
                    }
                    c += codeObj.len;
                    break;
                default:
                    decodeLog("Unknown extended operation code: " + opcodeBase);
                    throw new Error("Unknown extended operation code: " + opcodeBase);
            }
        }
        else
        {
            var opcodeBase = stream[c] & OP_BASE_MASK;

            switch (opcodeBase)
            {
                case CODE_ZERO_GROUP:
                    if ((stream[c] & MASK_SET_COLOR) == CODE_SET_COLOR)
                    {
                        codeObj = decodeColor(stream, c);
                        decodeLog("setColor " + JSON.stringify(codeObj));
                        var colorStr = rgbToHex(codeObj);
                        ctx.fillStyle=colorStr;
                        ctx.strokeStyle=colorStr;
                        c += codeObj.len;
                    }
                    else if ((stream[c] & MASK_SET_CLIP) == CODE_SET_CLIP)
                    {
                        codeObj = decodeRect(stream, c);
                        decodeLog( "setClip " + JSON.stringify(codeObj));
                        setClip(codeObj);
                        c += codeObj.len;
                    }
                    else if (stream[c] == CODE_PUSH_CLIP)
                    {
                        decodeLog( "pushCurrentClip");
                        pushCurrentClip();
                        c++;
                    }
                    else if (stream[c] == CODE_POP_CLIP)
                    {
                        decodeLog( "popCurrentClip");
                        popCurrentClip();
                        c++;
                    }
                    else
                    {
                        codeObj = decodeString(stream, c);
                        decodeLog( "drawString " + JSON.stringify(codeObj));
                        if (stringPools[componentIndex] && stringPools[componentIndex][codeObj.i])
                        {
                            fillMultilineTextNoWrap(stringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y);
                        }
                        c += codeObj.len;
                    }
                    break;
                case CODE_DRAW_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawRect " + JSON.stringify(codeObj));
                    ctx.strokeRect(codeObj.x+0.5, codeObj.y+0.5, codeObj.w, codeObj.h);
                    c+= codeObj.len;
                    break;
                case CODE_FILL_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "fillRect " + JSON.stringify(codeObj));
                    ctx.fillRect(codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    c+= codeObj.len;
                    break;
                case CODE_DRAW_OVAL:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawOval " + JSON.stringify(codeObj));
                    var r = codeObj.w/2;
                    ctx.beginPath();
                    ctx.arc(codeObj.x+r+0.5, codeObj.y+r+0.5, codeObj.w/2, 0, 2*Math.PI);
                    ctx.stroke();
                    c+= codeObj.len;
                    break;
                case CODE_FILL_OVAL:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "fillOval " + JSON.stringify(codeObj));
                    var r = codeObj.w/2-0.5;
                    if (r < 0)
                    {
                        r = 0;
                    }
                    ctx.beginPath();
                    ctx.arc(codeObj.x+r+0.5, codeObj.y+r+0.5, r, 0, 2*Math.PI);
                    ctx.fill();
                    c+= codeObj.len;
                    break;
                case CODE_DRAW_LINE:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawLine " + JSON.stringify(codeObj));
                    ctx.beginPath();
                    ctx.moveTo(codeObj.x+0.5, codeObj.y+0.5);
                    ctx.lineTo(codeObj.w+0.5, codeObj.h+0.5);
                    ctx.stroke();
                    c+= codeObj.len;
                    break;
                // TODO
                // Actually transfrom and clip are never used in a particular look vector (they are used between
                // painting components) so these commands may free places in the set of 1-byte commands
                case CODE_TRANSFORM:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "transform " + JSON.stringify(codeObj));
                    applyTransform(codeObj);
                    c+= codeObj.len;
                    break;
                case CODE_CLIP_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "clipRect " + JSON.stringify(codeObj));
                    clipRect(codeObj);
                    c+= codeObj.len;
                    break;
                default:
                    decodeLog( "Unknown operation code: " + stream[c]);
                    throw new Error("Unknown operation code: " + stream[c]);
            }
        }
    }
}

function processPositionMatrixUpdate(index)
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}

function processViewportMatrixUpdate(index)
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}

function processClipSizeUpdate(index)
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}

function processLookVectorUpdate(index)
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}

function processBooleanFlagMap(index)
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}

function processPaintAllList()
{
    var c = 1;
    while (c < paintAllSequence.byteLength)
    {
        c = paintComponent(paintAllSequence, c);
    }
    return c;
}

function processChildCountMap()
{
 // Do nothing: with canvas impl, whole cache is repainted at once
}


//var ci = 0;

function paintComponent(stream, c)
{
    var index = readShort(stream, c);
    c+=2;

    //var ici=ci;
    //ci++;

    try
    {
        var visible = checkFlagForComponent(index, STATE_FLAGS_VISIBILITY_MASK);

        // Find out child count
        var childCount = childCounts[index];

        //console.log("paintComponent:  index=" + index + " childCount=" + childCount);

        if (visible || (childCount > 0))
        {
            // Push current clip
            pushCurrentClip();

            // Position transformation
            var positionMatrix = positions[index];
            if (positionMatrix)
            {
                //if (ici < 3)
                 applyTransform(positionMatrix);


                absPositions[index] = currentTransform;
            }
            else
            {
                console.log("Position matrix undefined for " + index);
            }

            // Clip rect (or set entirely new clip if this is popup)
            var clipSize = clipSizes[index];
            if (clipSize)
            {
                var popup = checkFlagForComponent(index, STATE_FLAGS_POPUP_MASK);
                if (popup)
                {
                    setClip(clipSize);
                }
                else
                {
                    clipRect(clipSize);
                }
            }
            else
            {
                console.log("Clip undefined for " + index);
            }

            // Viewport transformation
            var viewportMatrix = viewports[index];
            if (viewportMatrix)
            {
                //if (ici < 3)
                 applyTransform(viewportMatrix);
            }
            else
            {
                console.log("Viewport matrix undefined for " + index);
            }

            // Paint component

            if (visible)
            {
                var lookVector = lookVectors[index];
                if (lookVector)
                {
                    if (lookVector.length > 0)
                    {
                        decodeLookVector(index, lookVector, lookVector.length);
                    }
                }
                else
                {
                   console.log("Look undefined for " + index);
                }
            }

            // Paint children
            for (var i=0; i<childCount; i++)
            {
                c = paintComponent(stream, c);
            }

            // Inverse viewport transformation
            if (viewportMatrix)
            {
                //if (ici < 3)
                 applyInverseTransform(viewportMatrix);
            }

            // Inverse position transformation
            if (positionMatrix)
            {
                //if (ici < 3)
                 applyInverseTransform(positionMatrix);
            }

            // Pop current clip
            popCurrentClip();
        }
        return c;
    }
    catch(e)
    {
        console.log("Error painting component with index = " + index + ": " + e.message);
        //throw(e);
    }
}

function repaintWholeCache()
{
    var pTime = Date.now();
    //ci=0;
    paintComponent(paintAllSequence, 1)
    var spentTime = Date.now() - pTime;
    //console.log("painting spentTime=" + spentTime);
}



/**
 * Client evolver processing
 */

var last_MX = lastMouseX;
var last_MY = lastMouseY;
function _TIME(){var d = new Date(); return d.getTime();};
function _TIME_DELTA(){return 0;};
function _MX(){return last_MX;}
function _MX_DELTA(){return 0;};
function _MY(){return last_MY;}
function _MY_DELTA(){return 0;};

function processClientEvolvers()
{
    _TIME_DELTA = function (){return millis - _TIME();}
    _MX_DELTA = function (){return _MX() - lastMouseX;}
    _MY_DELTA = function (){return _MY() - lastMouseY;}

    last_MY = lastMouseY;
    last_MX = lastMouseX;

    var needRepaint = false;
    for (var i=0; i<indicesWithClientEvolvers.length; i++)
    {
        var index = indicesWithClientEvolvers[i];
        if (clientEvolvers[index].position_matrix_M_dx)
        {
            positions[index].w += clientEvolvers[index].position_matrix_M_dx();
            needRepaint = true;
        }
        if (clientEvolvers[index].position_matrix_M_dy)
        {
            positions[index].h += clientEvolvers[index].position_matrix_M_dy();
            needRepaint = true;
        }
        if (clientEvolvers[index].position_matrix_M_x)
        {
            positions[index].w = clientEvolvers[index].position_matrix_M_x();
            needRepaint = true;
        }
        if (clientEvolvers[index].position_matrix_M_y)
        {
            positions[index].h = clientEvolvers[index].position_matrix_M_y();
            needRepaint = true;
        }
    }
    if (needRepaint)
    {
        repaintWholeCache();
    }
}


window.setInterval(processClientEvolvers, 33); // 33 for 30 FPS processing

function getHostBoundingClientRect()
{
    return canvas.getBoundingClientRect();
}

function isComponentReadyForMouseRollover(i)
{
    return absPositions[i] && !checkFlagForComponent(i, STATE_FLAGS_ROLLOVER_DISABLED_MASK);
}

// TODO do we really still need onEdge?
function getIndexUnderMouse(x, y)
{
    var indexUnderMouse;
    var onEdge = false;
    var t = function(a,b) {return Math.abs(a-b) < 2;};
    // Iterate from the end to hit tompost children first. Root is always at i=0.
    for (var i=absPositions.length-1; i>=0; i--)
    {
        if (isComponentReadyForMouseRollover(i))
        {
            var ix = absPositions[i][0][2];
            var iy = absPositions[i][1][2];

            if (x >= ix && y >= iy
                && x < ix+clipSizes[i].w && y < iy+clipSizes[i].h)
            {
                indexUnderMouse = i;
                onEdge = t(x, ix) || t(y, iy) || t(x, ix+clipSizes[i].w-1) || t(y, iy+clipSizes[i].h-1);
                break;
            }
        }
    }
}

canvas.addEventListener("mousedown", sendMouseDownEventToServer, false);
canvas.addEventListener("mouseup", sendMouseUpEventToServer, false);
canvas.addEventListener("click", sendMouseClickEventToServer, false);
canvas.addEventListener("mousemove", sendMouseMoveEventToServer, false);

canvas.ondragstart = function(e)
{
    if (e && e.preventDefault) { e.preventDefault(); }
    if (e && e.stopPropagation) { e.stopPropagation(); }
    return false;
}

canvas.onselectstart = function(e)
{
    if (e && e.preventDefault) { e.preventDefault(); }
    if (e && e.stopPropagation) { e.stopPropagation(); }
    return false;
}

// Adjust canvas size for window size, and send host-resize event to server

function getEncodedHostResizeEvent()
{
    return encodeHostSize(canvas.width, canvas.height)
}

// Start streaming

openSocket();