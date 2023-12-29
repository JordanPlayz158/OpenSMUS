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
 * Interface representing the OpenSMUS server instance.
 * <BR> ServerSideScript classes can use the serverObject() method to retrieve a pointer to a ServerObject.
 */
public interface ServerObject {

  /**
   * Displays a message to the server active output (log file or terminal window)
   */
  void put(String msg);

  /**
   * Retrieves a pointer to a ServerMovie object active on the server.
   *
   * @param moviename String with the name of the movie to be returned
   * @return ServerMovie pointer, or throws a MovieNotFoundException
   */
  ServerMovie getServerMovie(String moviename) throws MovieNotFoundException;

  /**
   * Retrieves a pointer to a ServerMovie object active on the server.
   *
   * @param movieidx index of the movie to be returned
   * @return ServerMovie pointer, or throws a MovieNotFoundException
   */
  ServerMovie getServerMovie(int movieidx) throws MovieNotFoundException;

  /**
   * Creates a new movie on the server and returns a pointer to it.
   *
   * @param moviename String with the name of the movie to be created
   * @return ServerMovie pointer, or throws a MUSErrorCode if the creation fails
   */
  ServerMovie createServerMovie(String moviename) throws MUSErrorCode;

  /**
   * Deletes a movie from the server.
   *
   * @param moviename String with the name of the movie to be deleted
   */
  void deleteServerMovie(String moviename);

  /**
   * Gets the number of active movies in the server.
   *
   * @return the number of movies
   */
  int serverMovieCount();

  /**
   * Gets the path of the server executable in the system
   */
  String path();

  /**
   * Gets the current server time formatted as a String
   */
  String timeString();

  /**
   * Gets the current server time as an int
   */
  int timeStamp();

  /**
   * This method is reserved. Current implementation returns 0 for the English version of OpenSMUS.
   */
  int language();

  /**
   * Gets the default user levels for users that connect to the server and do not have a user account in the DB.
   * <BR> This value can be configured by the DefaultUserLevel directive in OpenSMUS.cfg.
   */
  int userLevel();

  /**
   * Sets the default user levels for users that connect to the server and do not have a user account in the DB.
   * <BR> When this method is used it overrides the value configured by the DefaultUserLevel directive in OpenSMUS.cfg.
   */
  void setuserLevel(int level);

  /**
   * Returns a pointer to the SQLGateway interface representing the default SQL connection.
   * <BR> Please consult the documentation of SQLGateway for more information.
   */
  SQLGateway getSQLGateway();

  /**
   * Returns a pointer to the ServerUserDatabase interface representing the default user authentication table.
   * <BR> Please consult the documentation of ServerUserDatabase for more information.
   */
  ServerUserDatabase getServerUserDatabase();
}