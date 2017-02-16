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

var textMeasurer = document.createElementNS(svgNS, 'textMeasurer');
textMeasurer.setAttribute('x', '0');
textMeasurer.setAttribute('y', '0');
textMeasurer.setAttribute('fill', '#000');
textMeasurer.textContent = '';
hostSVG.appendChild(textMeasurer);

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

function measureTextImpl(s)
{
    textMeasurer.textContent = s;
    var w = text.getComputedTextLength();
    textMeasurer.textContent = '';
    return w;
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

function addImageToG(gElem, imageUrl, x, y, w, h)
{
    // TODO adjust image size to g size in case of draw mode?
    var im = document.createElementNS(svgNS, 'image');
    im.setAttribute('x', x);
    im.setAttribute('y', y);
    if (w && h)
    {
        im.setAttribute('width', w);
        im.setAttribute('height', h);
    }
    im.setAttribute('href', imageUrl);
    gElem.appendChild(im);
}

function setClipToG(gElem, codeObj)
{
//   currentTx = currentTransform[0][2];
//   currentTy = currentTransform[1][2];
//   currentClip = {x: currentTx, y: currentTy, w: codeObj.w, h: codeObj.h};
//   applyCurrentClip();
}

function setTextToG(gElem, text, x, y)
{
//    var lines = text.split("\n");
//    var lineHeight = getTextLineHeight();
//    for (var i=0; i<lines.length; i++)
//    {
//        fillText(lines[i], x, y + i*1.5*lineHeight);
//    }
    var t = document.createElementNS(svgNS, 'text');
    t.setAttribute('x', x);
    t.setAttribute('y', y);
//    if (currentlyDecodedColorStr)
//    {
//        t.setAttribute('fill', currentlyDecodedColorStr);
//    }
    t.setAttribute('fill', '#FFFFFF');
    t.textContent = text;
    gElem.appendChild(t);
}

function setRectToG(gElem, x, y, w, h)
{
    var r = document.createElementNS(svgNS,'rect');
    r.setAttribute('x',x);
    r.setAttribute('y',y);
    r.setAttribute('width',w);
    r.setAttribute('height',h);
    //r.setAttribute('fill','#35F3D7'); TODO ?? maintain 'current color for a g' while decoding new look vector?
    gElem.appendChild(r);
}

function setCircleToG(gElem, x, y, r)
{
    var c = document.createElementNS(svgNS,'circle');
    c.setAttribute('cx',x);
    c.setAttribute('cy',y);
    c.setAttribute('r',r);
    //c.setAttribute('fill','#D795B3');
    gElem.appendChild(c);
}

function setLineToG(gElem, x1, y1, x2, y2)
{
    var l = document.createElementNS(svgNS, 'line');
    l.setAttribute('x1', x1);
    l.setAttribute('y1', y1);
    l.setAttribute('x2', x2);
    l.setAttribute('y2', y2);
    l.setAttribute('stroke', '#000000');
    l.setAttribute('stroke-width', 1);
    gElem.appendChild(l);
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

//    var gShapesOld = gCElem.getElementById(genShapesSubElemId(index));
//    var gShapes = gShapesOld.cloneNode(false);
//    gCElem.replaceChild(gShapes, gShapesOld);
    var gShapes = document.getElementById(genShapesSubElemId(componentIndex));

    var gElem = gShapes;

    if (gElem.childElementCount > 0)
    {
//        var gElemOld = gElem;
//        gElem = gElem.cloneNode(false);
//        gElems[componentIndex] = gElem;
//        if (componentIndex != 0)
//        {
//            gElemOld.parentNode.replaceChild(gElem, gElemOld);
//        }

//        var childComponents = gElemOld.getElementsByTagName('g');
//        for (var cg=0; cg<childComponents.length; cg++)
//        {
//            gElem.appendChild(childComponents[cg]);
//        }
    }

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
                        //var img = getImage(componentIndex, imageUrl);
                        //ctx.drawImage(img, codeObj.x, codeObj.y);
                        addImageToG(gElem, imageUrl, codeObj.x, codeObj.y, null, null);
                    }
                    c += codeObj.len;
                    break;
                case CODE_FIT_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);

                    //var img = getImage(componentIndex, resourceStringPools[componentIndex][codeObj.i]);
                    //ctx.drawImage(img, codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    addImageToG(gElem, resourceStringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y, codeObj.w, codeObj.h);

                    c += codeObj.len;
                    break;
                case CODE_FILL_IMAGE_STRPOOL:
                    codeObj = decodeImageURIStrPool(stream, c);
                    var img = getImage(componentIndex, resourceStringPools[componentIndex][codeObj.i]);
                    var w = img.width;
                    var h = img.height;
                    if (codeObj.w <= w && codeObj.h <= h)
                    {
                        //TODO ctx.drawImage(img, codeObj.x, codeObj.y);
                    }
                    else if (w > 0 && h >0)
                    {
                        for (var ix=0; ix<codeObj.w; ix+=w)
                        {
                            for (var iy=0; iy<codeObj.h; iy+=h)
                            {
                                //TODO ctx.drawImage(img, codeObj.x+ix, codeObj.y+iy);
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
                       gElem.style.font = currentFont
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
                        gElem.style.fill = currentlyDecodedColorStr;

                        c += codeObj.len;
                    }
                    else if ((stream[c] & MASK_SET_CLIP) == CODE_SET_CLIP)
                    {
                        codeObj = decodeRect(stream, c);
                        decodeLog( "setClip " + JSON.stringify(codeObj) + " -- IGNORED");

                        //setClip(codeObj);

                        c += codeObj.len;
                    }
                    else if (stream[c] == CODE_PUSH_CLIP)
                    {
                        decodeLog( "pushCurrentClip -- IGNORED");

                        //pushCurrentClip();

                        c++;
                    }
                    else if (stream[c] == CODE_POP_CLIP)
                    {
                        decodeLog( "popCurrentClip -- IGNORED");

                        //popCurrentClip();

                        c++;
                    }
                    else
                    {
                        codeObj = decodeString(stream, c);
                        decodeLog( "drawString " + JSON.stringify(codeObj));
                        if (stringPools[componentIndex] && stringPools[componentIndex][codeObj.i])
                        {

                            //fillMultilineTextNoWrap(stringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y);
                            setTextToG(gElem, stringPools[componentIndex][codeObj.i], codeObj.x, codeObj.y);
                        }
                        c += codeObj.len;
                    }
                    break;
                case CODE_DRAW_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawRect " + JSON.stringify(codeObj));

                    //ctx.strokeRect(codeObj.x+0.5, codeObj.y+0.5, codeObj.w, codeObj.h);
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h);

                    c+= codeObj.len;
                    break;
                case CODE_FILL_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "fillRect " + JSON.stringify(codeObj));

                    //ctx.fillRect(codeObj.x, codeObj.y, codeObj.w, codeObj.h);
                    setRectToG(gElem, codeObj.x, codeObj.y, codeObj.w, codeObj.h); // TODO fill??

                    c+= codeObj.len;
                    break;
                case CODE_DRAW_OVAL:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "drawOval " + JSON.stringify(codeObj));
                    var r = codeObj.w/2;

                    //ctx.beginPath();
                    //ctx.arc(codeObj.x+r+0.5, codeObj.y+r+0.5, codeObj.w/2, 0, 2*Math.PI);
                    //ctx.stroke();
                    setCircleToG(gElem, codeObj.x+r, codeObj.y+r, r);

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
                    setCircleToG(gElem, codeObj.x+r, codeObj.y+r, r); // TODO fill

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

                    c+= codeObj.len;
                    break;
                // TODO 1
                // Actually transfrom and clip are never used in a particular look vector (they are used between
                // painting components) so these commands may free places in the set of 1-byte commands
                // TODO 2 - ... and round rect is really needed
                case CODE_TRANSFORM:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "transform " + JSON.stringify(codeObj) + " -- IGNORED");

                    //applyTransform(codeObj);

                    c+= codeObj.len;
                    break;
                case CODE_CLIP_RECT:
                    codeObj = decodeRect(stream, c);
                    decodeLog( "clipRect " + JSON.stringify(codeObj) + " -- IGNORED");

                    //clipRect(codeObj);

                    c+= codeObj.len;
                    break;
                default:
                    decodeLog( "Unknown operation code: " + stream[c]);
                    throw new Error("Unknown operation code: " + stream[c]);
            }
        }
    }
}

// TODO absPositions is needed for mouse handling
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
}

function processViewportMatrixUpdate(index)
{
    updateTransform(index);
}

function processClipSizeUpdate(index)
{
    var gElem = gElems[index];
    // TODO take into account popups
    gElem.setAttribute('width', clipSizes.w);
    gElem.setAttribute('height', clipSizes.h);
}

function processLookVectorUpdate(index)
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
    else
    {
        throw new Error("Paint all seq has been received already");
    }
}

function processChildCountMap()
{
    if (!childCountMapReceived)
    {
        childCountMapReceived = true;
        constructDOMIfPossible();
    }
    else
    {
        throw new Error("Child count map has been received already");
    }
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

        // TODO temporary
        gElems[0].style.font = '12px Tahoma'

        domConstructed=true;
    }
}

function createGElem(parent, index)
{
    var childCount = childCounts[paintAllArray[index]];

    console.log("Creating " + index + " and adding " + childCount + " children")

    var g = document.createElementNS(svgNS, "g");
    gElems[paintAllArray[index]] = g;

    var gShapes = document.createElementNS(svgNS, "g");
    gShapes.setAttribute('id', genShapesSubElemId(index));
    g.appendChild(gShapes);
    var gChildren = document.createElementNS(svgNS, "g");
    gChildren.setAttribute('id', genChildrenSubElemId(index));
    g.appendChild(gChildren);

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

function getEncodedHostResizeEvent()
{
    return encodeHostSize(hostSVG.width, hostSVG.height)
}

// Start streaming

openSocket();