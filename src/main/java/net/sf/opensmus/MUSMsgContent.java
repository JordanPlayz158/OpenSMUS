/*
  Part of OpenSMUS Source Code.
  OpenSMUS is licensed under a MIT License, compatible with both
  open source (GPL or not) and commercial development.

  Copyright (c) 2001-2008 Mauricio Piacentini <mauricio@tabuleiro.com>

  Permission is hereby granted, free of charge, to any person
  obtaining a copy of this software and associated documentation
  files (the "Software"), to deal in the Software without
  restriction, including without limitation the rights to use,
  copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the
  Software is furnished to do so, subject to the following
  conditions:

  The above copyright notice and this permission notice shall be
  included in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
  OTHER DEALINGS IN THE SOFTWARE.
*/

package net.sf.opensmus;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Vector;

/**
 * Class representing the content portion of a message formatted according to the Shockwave MultiUserServer specs.
 * <BR>See technote 15465 "Shockwave Multiuser protocol description" at <a href="http://go.adobe.com/kb/ts_tn_15465_en-us">...</a>
 * for more information about the internal structure of a Shockwave binary message.
 * Shockwave is a trademark of Adobe, Inc. All rights reserved.
 */

// @TODO: This class is never used. Delete?


public class MUSMsgContent {

  /**
   * Public vector element storing the LValue members.
   */
  public final Vector<LValue> m_list = new Vector<>();

  /**
   * Adds a LValue element to the message contents
   *
   * @param elem LValue to add
   */
  public void addElement(LValue elem) {
    m_list.addElement(elem);
  }

  /**
   * Fetches an LValue element from message contents list.
   * Usually messages contain only one element, a LList, which in turn contains other values.
   *
   * @param pos index of the element to be retrieved
   * @return LValue
   */
  public LValue getElementAt(int pos) {
    return m_list.elementAt(pos);
  }

  /**
   * Returns the number of elements in the message contents list
   */
  public int count() {
    return m_list.size();
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  public int extractFromBytes(byte[] rawBytes) {
    int chunkSize = 0;
    // int elemSize = 0;
    short elemType;
    LValue newVal;
    while (chunkSize < rawBytes.length) {
      elemType = ConversionUtils.byteArrayToShort(rawBytes, chunkSize);
      chunkSize = chunkSize + 2;

      newVal = switch (elemType) {
        case LValue.vt_Integer -> new LInteger();
        case LValue.vt_Symbol -> new LSymbol();
        case LValue.vt_String -> new LString();
        case LValue.vt_Picture -> new LPicture();
        case LValue.vt_Float -> new LFloat();
        case LValue.vt_List -> new LList();
        case LValue.vt_Point -> new LPoint();
        case LValue.vt_Rect -> new LRect();
        case LValue.vt_PropList -> new LPropList();
        case LValue.vt_Color -> new LColor();
        case LValue.vt_Date -> new LDate();
        case LValue.vt_Media -> new LMedia();
        case LValue.vt_3dVector -> new L3dVector();
        case LValue.vt_3dTransform -> new L3dTransform();
        default -> new LVoid();
      };
      chunkSize = chunkSize + newVal.extractFromBytes(rawBytes, chunkSize);
      m_list.addElement(newVal);

    }
    return chunkSize;

  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  public void dump() {

    for (LValue temp : m_list) {
      MUSLog.Log("content element: ", MUSLog.kDeb);
      temp.dump();
    }
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  public byte[] getBytes() {

    try {
      ByteArrayOutputStream bstream = new ByteArrayOutputStream(8192);
      DataOutputStream datastream = new DataOutputStream(bstream);

      for (LValue elem : m_list) {
        byte[] elemBytes = elem.getBytes();
        datastream.write(elemBytes, 0, elemBytes.length);
      }

      return bstream.toByteArray();
    } catch (Exception e) {
      MUSLog.Log("Error in msgcontent stream", MUSLog.kDeb);
      return "0".getBytes();
    }
  }
}
