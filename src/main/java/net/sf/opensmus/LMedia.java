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

/**
 * Class representing a Lingo compatible media value (LMedia for short).
 * Lingo is a trademark of Adobe, Inc. All rights reserved.
 */
public class LMedia extends LValue {

  private byte[] m_media;

  /**
   * Constructor
   */
  public LMedia(byte[] initbytes) {
    m_media = initbytes;
    setType(LValue.vt_Media);
  }

  /**
   * Constructor
   */
  public LMedia() {
    m_media = new byte[0];
    setType(LValue.vt_Media);
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public int extractFromBytes(byte[] rawBytes, int offset) {

    int byteSize = ConversionUtils.byteArrayToInt(rawBytes, offset);

    // Sanity check
    if (byteSize < 0 || byteSize > (rawBytes.length - offset - 4)) {
      MUSLog.Log("Media size error : " + byteSize + " " + rawBytes.length + " " + ConversionUtils.bytesToBinHex(rawBytes), MUSLog.kDeb);
      throw new NullPointerException("Media size error " + byteSize + " " + rawBytes.length);
    }

    m_media = new byte[byteSize];
    System.arraycopy(rawBytes, 4 + offset, m_media, 0, byteSize);

    int chunkSize = 4 + m_media.length;

    if ((byteSize % 2) != 0) chunkSize++;

    return chunkSize;
  }

  /**
   * Returns the byte array storing the media data in binary format
   */
  @Override
  public byte[] toBytes() {
    return m_media;
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public byte[] getBytes() {
    int finalSize = 6 + m_media.length;
    boolean addPadding = false;
    if ((finalSize % 2) != 0) {
      finalSize++;
      addPadding = true;
    }

    byte[] finalbytes = new byte[finalSize];
    ConversionUtils.shortToByteArray(getType(), finalbytes, 0);
    ConversionUtils.intToByteArray(m_media.length, finalbytes, 2);
    System.arraycopy(m_media, 0, finalbytes, 6, m_media.length);

    if (addPadding) finalbytes[finalSize - 1] = 0x00;

    return finalbytes;
  }

  /**
   * Reserved for internal use of OpenSMUS.
   */
  @Override
  public void dump() {
    MUSLog.Log("Media> " + ConversionUtils.bytesToBinHex(m_media), MUSLog.kDeb);
  }
}
