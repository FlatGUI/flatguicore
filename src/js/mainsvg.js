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
var gElems = [];
var paintAllArray = [];

var paintAllSequenceReceived = false;
var childCountMapReceived = false;
var domConstructed = false;

var LETTER_SPACING = 0;// Firefox does not support this yet https://bugzilla.mozilla.org/show_bug.cgi?id=371787

var lastMouseDetectX = -1;
var lastMouseDetectY = -1;
var indexUnderMouse;

var textMeasurer = document.createElementNS(svgNS, 'text');
textMeasurer.setAttribute('x', '0');
textMeasurer.setAttribute('y', '0');
textMeasurer.setAttribute('fill', '#000');
textMeasurer.textContent = '';
hostSVG.appendChild(textMeasurer);
var symbolMeasurements = {};

var defs = document.createElementNS(svgNS, 'defs');
hostSVG.appendChild(defs);

applyCursor("default");

function adjustSVGSize()
{
    hostSVG.setAttribute('width', window.innerWidth);
    hostSVG.setAttribute('height', window.innerHeight);
}
function handleResize(evt)
{
    adjustSVGSize();
    sendEventToServer(getEncodedHostResizeEvent());
}
window.onresize = handleResize;

function measure1CharText(charCode)
{
    var symbolMeasurementsF = symbolMeasurements[currentFont];
    if (symbolMeasurementsF == null)
    {
        symbolMeasurementsF = [];
        symbolMeasurements[currentFont] = symbolMeasurementsF;
    }
    var l = symbolMeasurementsF[charCode];
    if (l == null)
    {
        textMeasurer.textContent = String.fromCharCode(charCode);
        // Using round because Chrome (at least ver 59) yields decimal width that's more precise than needed
        var w = Math.round(textMeasurer.getComputedTextLength());
        textMeasurer.textContent = '';
        l = w+LETTER_SPACING;
        symbolMeasurementsF[charCode] = l;
        console.log("Measured W of " + String.fromCharCode(charCode) + " = " + l);
    }
    return l;
}

function measureTextImpl(s)
{
    if (s == " ")
    {
        s = "i";
    }

    var l = 0;
    for (var i=0; i<s.length; i++)
    {
        l += measure1CharText(s.charCodeAt(i));
    }
    return l;
}

function applyCurrentFont()
{
    textMeasurer.style.font = currentFont;
}

function repaintWholeCache()
{
}
function paintComponent(stream, c)
{
    return stream.byteLength-1
}

function genShapesSubElemId(index)
{
    return 's'+index;
}
function genChildrenSubElemId(index)
{
    return 'c'+index;
}

var currentlyDecodedColorStr;

var currentlyDecodedLookNodeList;
var currentlyDecodedLookNodeIndex;
var aheadCount;

function startLookDecoding(gElem)
{
    currentlyDecodedLookNodeList = gElem.childNodes;
    currentlyDecodedLookNodeIndex = 0;
    aheadCount = 0;
}

function endLookDecoding(gElem, shapeCount)
{
    var removeCount = gElem.childElementCount - shapeCount;
    while (removeCount > 0)
    {
        var c = currentlyDecodedLookNodeList[gElem.childElementCount-1];
        console.log("Removing " + c);
        gElem.removeChild(c);
        removeCount--;
    }
}

function canReuse(el, tagName, subTagName)
{
    return el != null && el.tagName == tagName && (subTagName == null || el.getAttribute('subTagName') == subTagName)
}

function reuseOrCreateElement(gElem, tagName, subTagName)
{
    var existingEl = currentlyDecodedLookNodeList[currentlyDecodedLookNodeIndex];
    currentlyDecodedLookNodeIndex++;
    if (canReuse(existingEl, tagName, subTagName))
    {
        return existingEl;
    }

    var newInst = document.createElementNS(svgNS, tagName);
    if (subTagName != null)
    {
        newInst.setAttribute('subTagName', subTagName);
    }
    if (existingEl != null)
    {
        //gElem.insertBefore(newInst, existingEl);
        gElem.replaceChild(newInst, existingEl);
        aheadCount++;
    }
    else
    {
        gElem.appendChild(newInst);
    }

    return newInst;
}

function addImageToG(gElem, componentIndex, imageUrl, x, y, w, h)
{
    var im = reuseOrCreateElement(gElem, 'image', null);
    im.setAttribute('x', x);
    im.setAttribute('y', y);
    if (w && h)
    {
        im.setAttribute('width', w);
        im.setAttribute('height', h);
    }
    else
    {
        var loadNotify = function(img){
            im.setAttribute('width', img.width);
            im.setAttribute('height', img.height);
        }
        getImage(componentIndex, imageUrl, loadNotify);
    }
    im.setAttribute('href', imageUrl);
}

function fillImageToG(gElem, componentIndex, imageUrl, x, y, w, h)
{
    var loadNotify = function(img){

        if (w <= img.width && h <= img.height)
        {
            addImageToG(gElem, componentIndex, imageUrl, x, y, w, h);
        }
        else
        {
            var patternId = 'pattern'+componentIndex;
            var pt
            var oldPt = document.getElementById(patternId);
            if (oldPt == null)
            {
                pt = document.createElementNS(svgNS, 'pattern');
                defs.appendChild(pt);
            }
            else
            {
                pt = oldPt;
            }

            pt.setAttribute('id', patternId);
            pt.setAttribute('x', x);
            pt.setAttribute('y', y);
            pt.setAttribute('width', img.width);
            pt.setAttribute('height', img.height);
            pt.setAttribute('patternUnits', "userSpaceOnUse");
            var im = oldPt == null ? document.createElementNS(svgNS, 'image') : pt.childNodes[0];
            im.setAttribute('x', 0);
            im.setAttribute('y', 0);
            im.setAttribute('width', img.width);
            im.setAttribute('height', img.height);
            im.setAttribute('href', imageUrl);
            pt.appendChild(im);

            var r = reuseOrCreateElement(gElem, 'rect', 'fillImage');
            r.setAttribute('x',x);
            r.setAttribute('y',y);
            r.setAttribute('width', w);
            r.setAttribute('height', h);
            r.style.fill = 'url(#'+patternId+')'
        }
    }
    getImage(componentIndex, imageUrl, loadNotify);
}

function addVideoToG(gElem, componentIndex, imageUrl, x, y, w, h)
{
    var vid = document.createElementNS("http://www.w3.org/1999/xhtml", 'video');
    //vid.setAttribute('x', x);
    //vid.setAttribute('y', y);
    vid.setAttribute('width', w);
    vid.setAttribute('height', h);
    var source = document.createElementNS("http://www.w3.org/1999/xhtml", 'source');
    source.setAttribute('src', imageUrl);
    vid.appendChild(source);

    // This works perfectly well in Firefox but does not work in Chrome
    // (the issue is discussed here https://stackoverflow.com/questions/8185845/svg-foreignobject-behaves-as-though-absolutely-positioned-in-webkit-browsers)
    //
    // So below is workaround: absolute-positioned outsize element - "video holder"
    //
//    var foreignObject = document.createElementNS(svgNS, 'foreignObject');
//    foreignObject.setAttribute('x', x);
//    foreignObject.setAttribute('y', y);
//    foreignObject.setAttribute('width', w);
//    foreignObject.setAttribute('height', h);
//    var foDiv = document.createElementNS("http://www.w3.org/1999/xhtml", 'div');
//    foDiv.setAttribute('x', x);
//    foDiv.setAttribute('y', y);
//    foDiv.setAttribute('width', w);
//    foDiv.setAttribute('height', h);
//    foDiv.setAttribute('xmlns', "http://www.w3.org/1999/xhtml");
//    foDiv.appendChild(vid);
//    foreignObject.appendChild(foDiv);
//    gElem.appendChild(foreignObject);

    addVideoHolder(componentIndex, vid, x, y, w, h);
}

// BEGIN "video holder" workaround

var videoHolders = [];

function getVideoHolderId(componentIndex, holderIndex)
{
    return "video"+componentIndex+"_"+holderIndex;
}

function addVideoHolder(componentIndex, vid, x, y, w, h)
{
    var componentVideoHolders = videoHolders[componentIndex];
    if (componentVideoHolders == null)
    {
        componentVideoHolders = [];
        videoHolders[componentIndex] = componentVideoHolders;
    }
    var holderIndex = componentVideoHolders.length;

    var id = getVideoHolderId(componentIndex, holderIndex);
    var d = document.createElement("div")
    d.id = id;
    d.style.position = 'absolute'
    var absPos = getComponentAbsPosition(componentIndex);
    d.style.left = (absPos.x + x) + 'px';
    d.style.top = (absPos.y + y) + 'px';
    d.setAttribute('relX', x);
    d.setAttribute('relY', y);
    d.setAttribute('width', w);
    d.setAttribute('height', h);
    d.style.width = w + 'px';
    d.style.height = h + 'px';
    var visible = x + viewports[componentIndex].w >= 0 && y + viewports[componentIndex].h >= 0
        && (x + viewports[componentIndex].w + w) < clipSizes[componentIndex].w && (y + viewports[componentIndex].h + h) < clipSizes[componentIndex].h;
    d.style.visibility = visible ? 'visible' : 'hidden';

    d.appendChild(vid);
    document.body.appendChild(d);

    componentVideoHolders[holderIndex] = d;
}

function updateVideoHoldersAbsPos()
{
    for (var componentIndex=0; componentIndex<videoHolders.length; componentIndex++)
    {
        var absPos = getComponentAbsPosition(componentIndex);

        var componentVideoHolders = videoHolders[componentIndex];
        if (componentVideoHolders != null)
        {
            for (var holderIndex=0; holderIndex<componentVideoHolders.length; holderIndex++)
            {
                var x = parseInt(componentVideoHolders[holderIndex].getAttribute('relX'));
                var y = parseInt(componentVideoHolders[holderIndex].getAttribute('relY'));
                var w = parseInt(componentVideoHolders[holderIndex].getAttribute('width'));
                var h = parseInt(componentVideoHolders[holderIndex].getAttribute('height'));
                componentVideoHolders[holderIndex].style.left = (absPos.x + x) + 'px';
                componentVideoHolders[holderIndex].style.top = (absPos.y + y) + 'px';

                var visible = x + viewports[componentIndex].w >= 0 && y + viewports[componentIndex].h >= 0
                    && (x + viewports[componentIndex].w + w) < clipSizes[componentIndex].w && (y + viewports[componentIndex].h + h) < clipSizes[componentIndex].h;
                componentVideoHolders[holderIndex].style.visibility = visible ? 'visible' : 'hidden';
            }
        }
    }
}

var needVideoHoldersPosUpdate = false;
function onCommandVectorProcessed()
{
    if (needVideoHoldersPosUpdate)
    {
        updateVideoHoldersAbsPos();
        needVideoHoldersPosUpdate = false;
    }
}
// // // END "video holder" workaround

function setClipToG(gElem, codeObj)
{
}

function setColorToPrimitive(gElem, p, fill)
{
    if (fill)
    {
        p.setAttribute('fill', currentlyDecodedColorStr);
    }
    else
    {
        p.setAttribute('stroke', currentlyDecodedColorStr);
        p.setAttribute('fill-opacity', '0');
    }
}

function setFontToPrimitive(gElem, p, fill)
{
    p.style.font = currentFont
}

// TODO reuse
function setTextToG(gElem, componentIndex, text, x, y)
{
    var preciseTextMeasurement = checkFlagForComponent(componentIndex, STATE_FLAGS_PRECISE_TEXT_MEASUREMENT);
    if (preciseTextMeasurement)
    {
        var g = reuseOrCreateElement(gElem, 'g', 'preciseText');
        setColorToPrimitive(gElem, g, true);
        setFontToPrimitive(gElem, g);

        var toAppend = [];
        var toAppendCount = 0;

        for (var j=0; j<text.length; j++)
        {
            var c = text.charAt(j);
            var t;
            if (j < g.childElementCount)
            {
                t = g.childNodes[j];
            }
            else
            {
                t = document.createElementNS(svgNS, 'text');
                toAppend[toAppendCount] = t;
                toAppendCount++;
            }
            t.setAttribute('x', x);
            t.setAttribute('y', y);
            t.textContent = c;

            x += measureTextImpl(c);
        }

        if (text.length < g.childElementCount)
        {
            var toRemove = [];
            var toRemoveCount = g.childElementCount-text.length;
            for (j=0; j<toRemoveCount; j++)
            {
                toRemove[j] = g.childNodes[text.length+j];
            }
            for (j=0; j<toRemoveCount; j++)
            {
                g.removeChild(toRemove[j]);
            }
        }
        else
        {
            for (j=0; j<toAppendCount; j++)
            {
                g.appendChild(toAppend[j]);
            }
        }
    }
    else
    {
        var t = reuseOrCreateElement(gElem, 'text');
        t.setAttribute('x', x);
        t.setAttribute('y', y);
        t.setAttribute('kerning', 0);
        t.setAttribute('letter-spacing', LETTER_SPACING);
        t.setAttribute('word-spacing', 0);
        setColorToPrimitive(gElem, t, true);
        setFontToPrimitive(gElem, t);
        t.textContent = text;
    }
}

function setRectToG(gElem, x, y, w, h, rad, fill)
{
    var r = reuseOrCreateElement(gElem, 'rect', fill ? 'fill' : 'draw');
    r.setAttribute('x',x);
    r.setAttribute('y',y);
    if (rad > 0)
    {
        r.setAttribute('rx',rad);
        r.setAttribute('ry',rad);
    }
    r.setAttribute('width',w);
    r.setAttribute('height',h);
    setColorToPrimitive(gElem, r, fill);
}

function setCircleToG(gElem, x, y, r, fill)
{
    var c = reuseOrCreateElement(gElem, 'circle', fill ? 'fill' : 'draw');
    c.setAttribute('cx',x);
    c.setAttribute('cy',y);
    c.setAttribute('r',r);
    setColorToPrimitive(gElem, c, fill);
}

function setLineToG(gElem, x1, y1, x2, y2)
{
    var l = reuseOrCreateElement(gElem, 'line', null);
    l.setAttribute('x1', x1);
    l.setAttribute('y1', y1);
    l.setAttribute('x2', x2);
    l.setAttribute('y2', y2);
    setColorToPrimitive(gElem, l, false);
    l.setAttribute('stroke-width', 1);
}



function displayUserTextMessage(msg, x, y)
{
    console.log(msg);
}

function displayStatus(msg)
{
    console.log(msg);
}



function decodeLog(msg)
{
    //console.log(msg);
}

function decodeCVLog(msg)
{
    //console.log(msg);
}

function decodeLookVector(componentIndex, stream, byteLength)
{
    var gCElem = gElems[componentIndex]

    var gShapes = document.getElementById(genShapesSubElemId(componentIndex));

    var componentVideoHolders = videoHolders[componentIndex];
    if (componentVideoHolders != null)
    {
        for (var v=0; v<componentVideoHolders.length; v++)
        {
            componentVideoHolders[v].parentNode.removeChild(componentVideoHolders[v]);
        }
        videoHolders[componentIndex] = null;
    }

    var gElem = gShapes;
    startLookDecoding(gElem);
    var shapeCount = 0;

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
                        imageUrl = resourceUriPrefix+imageUrl;
                        addImageToG(gElem, componentIndex, imageUrl, codeObj.x, codeObj.y, null, null);
                        shapeCount++;
                    }
                    c += codeObj.len;
                    break;
                case CODE_FIT_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    imageUrl = resourceUriPrefix+resourceStringPools[componentIndex][codeObj.i];
                    addImageToG(gElem, componentIndex, imageUrl, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    shapeCount++;
                    c += codeObj.len;
                    break;
                case CODE_FILL_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    imageUrl = resourceUriPrefix+resourceStringPools[componentIndex][codeObj.i];
                    fillImageToG(gElem, componentIndex, imageUrl, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    shapeCount++;
                    c += codeObj.len;
                    break;
                case CODE_FIT_VIDEO_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    videoUrl = resourceUriPrefix+resourceStringPools[componentIndex][codeObj.i];
                    addVideoToG(gElem, componentIndex, videoUrl, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    shapeCount++;
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

                       //gElem.style.font = currentFont
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
                        currentlyDecodedColorStr = rgbToHex(codeObj);

                        //ctx.fillStyle=colorStr;
                        //ctx.strokeStyle=colorStr;
                        //gElem.style.fill = currentlyDecodedColorStr;

                        c += codeObj.len;
                    }
//                    else if ((stream[c] & MASK_SET_CLIP) == CODE_SET_CLIP)
//                    {
//                        codeObj = decodeRect(stream, c);
//                        decodeLog( "setClip " + JSON.stringify(codeObj) + " -- IGNORED");
//
//                        //setClip(codeObj);
//
//                        c += codeObj.len;
//                    }
//                    else if (stream[c] == CODE_PUSH_CLIP)
//                    {
//                        decodeLog( "pushCurrentClip -- IGNORED");
//
//                        //pushCurrentClip();
//
//                        c++;
//                    }
//                    else if (stream[c] == CODE_POP_CLIP)
//                    {
//                        decodeLog( "popCurrentClip -- IGNORED");
//
//                        //popCurrentClip();
//
//                        c++;
//                    }
                    else
                    {
                        codeObj = decodeString(stream, c);
                        decodeLog( "drawString " + JSON.stringify(codeObj));
                        if (stringPools[componentIndex] && stringPools[componentIndex][codeObj.i])
                        {

                            //fillMultilineTextNoWrap(stringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y);
                            setTextToG(gElem, componentIndex, stringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y);
                            shapeCount++;
                        }
                        c += codeObj.len;
                    }
                    break;
                case CODE_DRAW_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawRect " + JSON.stringify(codeObj));

                    //ctx.strokeRect(codeObj.x+0.5, codeObj.y+0.5, codeObj.w, codeObj.h);
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h, 0, false);
                    shapeCount++;

                    c+= codeObj.len;
                    break;
                case CODE_FILL_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "fillRect " + JSON.stringify(codeObj));

                    //ctx.fillRect(codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h, 0, true);
                    shapeCount++;

                    c+= codeObj.len;
                    break;
                case CODE_DRAW_OVAL:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawOval " + JSON.stringify(codeObj));
                    var r = codeObj.w/2;

                    //ctx.beginPath();
                    //ctx.arc(codeObj.x+r+0.5, codeObj.y+r+0.5, codeObj.w/2, 0, 2*Math.PI);
                    //ctx.stroke();
                    setCircleToG(gElem, codeObj.x+r, codeObj.y+r, r, false);
                    shapeCount++;

                    c+= codeObj.len;
                    break;
                case CODE_FILL_OVAL:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "fillOval " + JSON.stringify(codeObj));
                    //var r = codeObj.w/2-0.5;
                    var r = codeObj.w/2;
                    if (r < 0)
                    {
                        r = 0;
                    }

                    //ctx.beginPath();
                    //ctx.arc(codeObj.x+r+0.5, codeObj.y+r+0.5, r, 0, 2*Math.PI);
                    //ctx.fill();
                    setCircleToG(gElem, codeObj.x+r, codeObj.y+r, r, true);
                    shapeCount++;

                    c+= codeObj.len;
                    break;
                case CODE_DRAW_LINE:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawLine " + JSON.stringify(codeObj));

                    //ctx.beginPath();
                    //ctx.moveTo(codeObj.x+0.5, codeObj.y+0.5);
                    //ctx.lineTo(codeObj.w+0.5, codeObj.h+0.5);
                    //ctx.stroke();
                    setLineToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    shapeCount++;

                    c+= codeObj.len;
                    break;
                // Actually transfrom and clip are never used in a particular look vector (they are used between
                // painting components) so these commands may free places in the set of 1-byte commands
//                case CODE_TRANSFORM:
//                    codeObj = decodeRect(stream, c);
//                    decodeLog( "transform " + JSON.stringify(codeObj) + " -- IGNORED");
//
//                    //applyTransform(codeObj);
//
//                    c+= codeObj.len;
//                    break;
//                case CODE_CLIP_RECT:
//                    codeObj = decodeRect(stream, c);
//                    decodeLog( "clipRect " + JSON.stringify(codeObj) + " -- IGNORED");
//
//                    //clipRect(codeObj);
//
//                    c+= codeObj.len;
//                    break;
                case CODE_DRAW_ROUND_RECT:
                    codeObj = decodeRoundRect(stream, c);
                    decodeLog( "drawRoundRect " + JSON.stringify(codeObj));
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h, codeObj.r, false);
                    shapeCount++;
                    c+= codeObj.len;
                    break;
                case CODE_FILL_ROUND_RECT:
                    codeObj = decodeRoundRect(stream, c);
                    decodeLog( "drawRoundRect " + JSON.stringify(codeObj));
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h, codeObj.r, true);
                    shapeCount++;
                    c+= codeObj.len;
                    break;
                default:
                    decodeLog( "Unknown operation code: " + stream[c]);
                    throw new Error("Unknown operation code: " + stream[c]);
            }
        }
    }
    endLookDecoding(gElem, shapeCount);
}

function getClipId(index)
{
    return 'clip'+index;
}

function updateTransform(index)
{
    var tx=0, ty=0;
    if (positions[index])
    {
        tx+=positions[index].w;
        ty+=positions[index].h;
    }
    if (viewports[index])
    {
        tx+=viewports[index].w;
        ty+=viewports[index].h;
    }
    var gElem = gElems[index];
    gElem.setAttribute('transform','translate('+tx+','+ty+')');
}

function processPositionMatrixUpdate(index)
{
    updateTransform(index);
    needVideoHoldersPosUpdate = true;
}

function processViewportMatrixUpdate(index)
{
    updateTransform(index);
    needVideoHoldersPosUpdate = true;
    if (viewports[index])
    {
        var clipId = getClipId(index);
        var clippath = document.getElementById(clipId);
        if (clippath)
        {
            var cr = clippath.childNodes[0]
            cr.setAttribute('x', -viewports[index].w);
            cr.setAttribute('y', -viewports[index].h);
        }
    }
}

function processClipSizeUpdate(index)
{
    var gElem = gElems[index];

    // TODO Take into account popups. Most like they will have to be reassigned to the root when popped up and then returned back
    var clipId = getClipId(index);
    var clippath = document.getElementById(clipId);
    var cr;
    if (clippath)
    {
        cr = clippath.childNodes[0];
    }
    else
    {
        clippath = document.createElementNS(svgNS, 'clipPath');
        cr = document.createElementNS(svgNS, 'rect');
        cr.setAttribute('x', '0');
        cr.setAttribute('y', '0');
        clippath.setAttribute('id', clipId);
        clippath.appendChild(cr);
        gElem.appendChild(clippath);
        gElem.setAttribute('clip-path', 'url(#'+clipId+')');
    }
    cr.setAttribute('width', clipSizes[index].w);
    cr.setAttribute('height', clipSizes[index].h);
}

function processBooleanFlagMap(index)
{
    var gElem = gElems[index];
    if (gElem != null)
    {
    var visible = checkFlagForComponent(index, STATE_FLAGS_VISIBILITY_MASK);
    gElem.setAttribute('visibility', visible ? 'visible' : 'hidden');
    processLookVectorUpdate(index);
    }
    else
    {
        console.log("Accessing null index " + index)
    }
}

function processLookVectorUpdate(index)
{
    var lookVector = lookVectors[index];
    if (lookVector)
    {
        decodeLookVector(index, lookVector, lookVector.length);
    }
    else
    {
        console.log("Look undefined for " + index);
    }
}

function processPaintAllList()
{
    if (!paintAllSequenceReceived)
    {
        paintAllSequenceReceived = true;
        var cc = 1
        var i=0;
        while(cc < paintAllSequence.byteLength)
        {
            var cci = readShort(paintAllSequence, cc);
            cc+=2;
            paintAllArray[i]=cci;
            i++;
        }
        constructDOMIfPossible();
    }
}

function processChildCountMap()
{
    if (!childCountMapReceived)
    {
        childCountMapReceived = true;
        constructDOMIfPossible();
    }
}

function applyCursor(cursor)
{
    hostSVG.style.cursor = cursor;
}

function getComponentAbsPosition(index)
{
    var gElem = gElems[index]
    var clientRect = gElem.getBoundingClientRect();
    var x = clientRect.left;
    var y = clientRect.top;
    return {x: x, y: y}
}

function constructDOMIfPossible()
{
    if (paintAllSequenceReceived && childCountMapReceived && !domConstructed)
    {
        if (childCounts.length != (paintAllSequence.byteLength-1)/2)
        {
            throw new Error("Child count map size = " + childCounts.length +
                " is inconsistent with paintAllSequence.byteLength = " + paintAllSequence.byteLength);
        }

        console.log("Cosntructing DOM. Total elem count = " + childCounts.length);

        createGElem(null, 0);
        hostSVG.appendChild(gElems[0]);

        // Default font in case application does not define its own. This is equal to FGWebInteropUtil.getDefaultFont
        gElems[0].style.font = '12px Tahoma'

        domConstructed=true;
    }
}

function createGElem(parent, index)
{
    var componentUid = paintAllArray[index];
    var childCount = childCounts[componentUid];

    var g = createGElemImpl(componentUid);

    if (childCount > 0)
    {
        var gChildren = createChildHolder(g, componentUid);
    }

    if (parent)
    {
        parent.appendChild(g);
    }
    index++;

    var i = 0;
    while (i < childCount)
    {
        index = createGElem(gChildren, index)
        i++;
    }

    return index;
}

function createGElemImpl(componentUid)
{
    var g = document.createElementNS(svgNS, "g");
    g.setAttribute('id', componentUid);
    gElems[componentUid] = g;
    g.addEventListener('mousemove', function(evt)
    {
        var rect = getHostBoundingClientRect();
        var x = evt.clientX - rect.left;
        var y = evt.clientY - rect.top;
        if (x != lastMouseDetectX || y != lastMouseDetectY)
        {
            indexUnderMouse = this.getAttribute('id')
            lastMouseDetectX = x;
            lastMouseDetectY = y;
        }
    });

    var gShapes = document.createElementNS(svgNS, "g");
    gShapes.setAttribute('id', genShapesSubElemId(componentUid));
    g.appendChild(gShapes);

    return g;
}

function createChildHolder(g, componentUid)
{
    var gChildren = document.createElementNS(svgNS, "g");
    gChildren.setAttribute('id', genChildrenSubElemId(componentUid));
    g.appendChild(gChildren);
    return gChildren;
}

function componentRemoveImpl(index)
{
    console.log("Removing SVG node for " + index)
    gElems[index].parentNode.removeChild(gElems[index]);
    gElems[index] = null;
}

function addComponentImpl(parentIndex, addedChildIndex)
{
    console.log("Adding SVG node for " + addedChildIndex)
    var gAdded = createGElemImpl(addedChildIndex);

    var gChildren = document.getElementById(genChildrenSubElemId(parentIndex));
    if (gChildren == null)
    {
        gChildren = createChildHolder(gElems[parentIndex], parentIndex);
    }
    gChildren.appendChild(gAdded);
}

function getHostBoundingClientRect()
{
    return hostSVG.getBoundingClientRect();
}

function getIndexUnderMouse(x, y)
{
    return indexUnderMouse;
}

hostSVG.addEventListener("mousedown", sendMouseDownEventToServer, false);
hostSVG.addEventListener("mouseup", sendMouseUpEventToServer, false);
hostSVG.addEventListener("click", sendMouseClickEventToServer, false);
hostSVG.addEventListener("mousemove", sendMouseMoveEventToServer, false);

hostSVG.addEventListener("wheel", sendMouseWheelEventToServer);

function getEncodedHostResizeEvent()
{
    var bBox = hostSVG.getBoundingClientRect();
    return encodeHostSize(bBox.width, bBox.height);
}

// Start streaming

showSplash();
openSocket();