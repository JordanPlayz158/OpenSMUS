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
import java.util.Enumeration;
import java.util.Vector;

/**
 * Class representing a Lingo compatible List value (LList for short).
 * Lingo is a trademark of Adobe, Inc. All rights reserved.
 */
public class LPropList extends LValue {

  /**
   * Public vector element storing the property names as LSymbols
   */
  public final Vector<LValue> m_proplist;
  /**
   * Public vector element storing the list members as LValues
   */
  public final Vector<LValue> m_list;

  /**
   * Constructor
   */
  public LPropList() {
    m_proplist = new Vector<>();
    m_list = new Vector<>();
    setType(LValue.vt_PropList);
  }

  /**
   * Adds an LValue element to the list
   *
   * @param property LSymbol with property name
   * @param elem     LValue to add
   */
  public void addElement(LValue property, LValue elem) {
    m_proplist.addElement(property);
    m_list.addElement(elem);
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
   * Fetches an LValue property name from the list
   *
   * @param pos index of the property to be retrieved
   * @return LValue
   */
  public LValue getPropAt(int pos) {
    return m_proplist.elementAt(pos);
  }

  /**
   * Fetches an LValue element from the list
   *
   * @param prop LSymbol representing the property name
   * @return LValue
   */
  public LValue getElement(LSymbol prop) throws PropertyNotFoundException {

    // Enumeration enume = m_proplist.elements();
    int idx = 0;
    // LValue elem;
    // while (enume.hasMoreElements()) {
    for (LValue elem : m_proplist) {
      // elem = (LValue) enume.nextElement();
      if (elem.getType() != LValue.vt_Symbol) continue;
      LSymbol sym = (LSymbol) elem;
      if (prop.toString().equalsIgnoreCase(sym.toString())) {
        return m_list.elementAt(idx);
      }
      idx++;
    }

    // return new LVoid();
    throw new PropertyNotFoundException(prop.toString());
  }

  /**
   * Returns the number of elements in the list
   */
  public int count() {
    return m_proplist.size();
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public int extractFromBytes(byte[] rawBytes, int offset) {
    int numOfElems = ConversionUtils.byteArrayToInt(rawBytes, offset);
    int chunkSize = 4;
    short elemType;
    LValue newProp;
    LValue newVal;
    for (int i = 0; i < numOfElems; i++) {
      // Extract prop
      // Extract element type (should be symbol anyway) //corrected to accept any type
      elemType = ConversionUtils.byteArrayToShort(rawBytes, offset + chunkSize);
      chunkSize += 2;

      newProp = switch (elemType) {
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

      chunkSize = chunkSize + newProp.extractFromBytes(rawBytes, offset + chunkSize);
      m_proplist.addElement(newProp);

      // Extract element
      elemType = ConversionUtils.byteArrayToShort(rawBytes, offset + chunkSize);
      chunkSize += 2;

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

    for (LValue temp : m_proplist) {
      MUSLog.Log("proplist property: ", MUSLog.kDeb);
      temp.dump();
    }

    for (LValue temp : m_list) {
      MUSLog.Log("proplist element: ", MUSLog.kDeb);
      temp.dump();
    }
  }


  @Override
  public String toString() {

    StringBuilder s = new StringBuilder("[");
    for (int n = 0; n < m_proplist.size(); n++) {
      s.append(m_proplist.get(n).toString());
      s.append(": ");
      s.append(m_list.get(n).toString());
      s.append(", ");
    }
    if (s.length() > 2) s.setLength(s.length() - 2);
    s.append("]");
    return s.toString();
  }


  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public byte[] getBytes() {

    try {
      ByteArrayOutputStream bstream = new ByteArrayOutputStream(2);
      DataOutputStream datastream = new DataOutputStream(bstream);

      datastream.writeShort(vt_PropList);
      datastream.writeInt(m_proplist.size());

      byte[] elemBytes;
      LValue elem;
      Enumeration<LValue> enume = m_proplist.elements();
      Enumeration<LValue> enum2 = m_list.elements();
      while (enume.hasMoreElements()) {
        elem = enume.nextElement();
        elemBytes = elem.getBytes();
        datastream.write(elemBytes, 0, elemBytes.length);
        elem = enum2.nextElement();
        elemBytes = elem.getBytes();
        datastream.write(elemBytes, 0, elemBytes.length);
      }

      return bstream.toByteArray();
    } catch (IOException e) {
      MUSLog.Log("Error in LPropList stream", MUSLog.kSys);
      return "0".getBytes();
    }
  }

}
