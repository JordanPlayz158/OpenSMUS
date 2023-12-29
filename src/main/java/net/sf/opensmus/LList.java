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
import java.io.IOException;
import java.util.Vector;

/**
 * Class representing a Lingo compatible List value (LList for short).
 * Lingo is a trademark of Adobe, Inc. All rights reserved.
 */
public class LList extends LValue {

  /**
   * Public vector element storing the list members as LValues
   */
  public final Vector<LValue> m_list;

  /**
   * Constructor
   */
  public LList() {
    m_list = new Vector<>();
    setType(LValue.vt_List);
  }

  /**
   * Adds an LValue element to the list
   *
   * @param elem LValue to add
   */
  public void addElement(LValue elem) {
    m_list.addElement(elem);
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public int extractFromBytes(byte[] rawBytes, int offset) {
    int numOfElems = ConversionUtils.byteArrayToInt(rawBytes, offset);
    int chunkSize = 4;
    short elemType;
    LValue newVal;
    for (int i = 0; i < numOfElems; i++) {
      elemType = ConversionUtils.byteArrayToShort(rawBytes, offset + chunkSize);
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
      chunkSize = chunkSize + newVal.extractFromBytes(rawBytes, offset + chunkSize);
      m_list.addElement(newVal);

    }
    return chunkSize;

  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public void dump() {

    for (LValue temp : m_list) {
      MUSLog.Log("list element: ", MUSLog.kDeb);
      temp.dump();
    }
  }


  @Override
  public String toString() {

    StringBuilder s = new StringBuilder("[");
    for (LValue temp : m_list) {
      s.append(temp.toString());
      s.append(", ");
    }
    if (s.length() > 2) s.setLength(s.length() - 2);
    s.append("]");
    return s.toString();
  }


  /**
   * Fetches an LValue element from the list
   *
   * @param pos index of the element to be retrieved
   * @return LValue
   */
  public LValue getElementAt(int pos) {
    return m_list.elementAt(pos);
  }

  /**
   * Stores an LValue element at the specified position
   *
   * @param pos  position to store the element
   * @param elem LValue to store
   * @return boolean
   */
  public boolean setElementAt(int pos, LValue elem) {
    if (pos >= m_list.size())
      return false;

    m_list.setElementAt(elem, pos);
    return true;
  }

  /**
   * Returns the number of elements in the list
   */
  public int count() {
    return m_list.size();
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public byte[] getBytes() {

    try {
      ByteArrayOutputStream bstream = new ByteArrayOutputStream(2);
      DataOutputStream datastream = new DataOutputStream(bstream);

      datastream.writeShort(vt_List);
      datastream.writeInt(m_list.size());

      for (LValue elem : m_list) {
        byte[] elemBytes = elem.getBytes();
        datastream.write(elemBytes, 0, elemBytes.length);
      }

      return bstream.toByteArray();
    } catch (IOException e) {
      MUSLog.Log("Error in LList stream", MUSLog.kSys);
      return "0".getBytes();
    }
  }

}
