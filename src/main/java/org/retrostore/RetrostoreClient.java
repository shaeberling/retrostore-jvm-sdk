/*
 * Copyright 2017, Sascha Häberling
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.retrostore;

import org.retrostore.client.common.proto.App;
import org.retrostore.client.common.proto.AppNano;
import org.retrostore.client.common.proto.MediaImage;
import org.retrostore.client.common.proto.MediaImageRef;
import org.retrostore.client.common.proto.MediaType;
import org.retrostore.client.common.proto.SystemState;

import java.util.List;
import java.util.Set;

/**
 * Common Retrostore client interface.
 */
public interface RetrostoreClient {
  /**
   * Fetches data about the RetroStore app item with the given ID.
   *
   * @param appId the ID of the app
   * @return the app data for the app if it exists, otherwise null.
   * @throws ApiException
   */
  App getApp(String appId) throws ApiException;

  /**
   * Fetches a number of RetroStore app items. Blocks until results are
   * received.
   *
   * @param start the index at which to start.
   * @param num   the number of app items to fetch (max).
   * @return A list of the items requested or an error, if something
   * went wrong.
   */
  List<App> fetchApps(int start, int num) throws ApiException;

  /**
   * Like {@link #fetchApps(int, int)} but adds options.
   */
  List<App> fetchApps(int start, int num, String searchQuery,
                      Set<MediaType> hasMediaTypes)
      throws ApiException;

  /**
   * Fetches a number of RetroStore app items. Blocks until results are
   * received.
   *
   * @param start the index at which to start.
   * @param num   the number of app items to fetch (max).
   * @return A list of the items requested or an error, if something
   * went wrong.
   */
  List<AppNano> fetchAppsNano(int start, int num) throws ApiException;

  /**
   * Like {@link #fetchApps(int, int)} but adds options.
   */
  List<AppNano> fetchAppsNano(int start, int num, String searchQuery,
                              Set<MediaType> hasMediaTypes)
      throws ApiException;

  /**
   * Fetches the media images for the app with the given ID.
   *
   * @param appId the ID of the app for which to fetch the media images.
   * @return A list of all the media images fetched for this app.
   */
  List<MediaImage> fetchMediaImages(String appId) throws ApiException;

  /**
   * Fetches the media images for the app with the given ID.
   *
   * @param appId the ID of the app for which to fetch the media images.
   * @param types only include images of the given types.
   * @return A list of all the media images fetched for this app.
   */
  List<MediaImage> fetchMediaImages(String appId, Set<MediaType> types) throws ApiException;

  /**
   * Fetches the media image references for the app with the given ID.
   *
   * @param appId the ID of the app for which to fetch the media images.
   * @return A list of all the media image references for this app.
   */
  List<MediaImageRef> fetchMediaImageRefs(String appId) throws ApiException;

  /**
   * Fetches the media image references for the app with the given ID.
   *
   * @param appId the ID of the app for which to fetch the media images.
   * @param types only include images of the given types.
   * @return A list of all the media image references for this app.
   */
  List<MediaImageRef> fetchMediaImageRefs(String appId, Set<MediaType> types) throws ApiException;

  /**
   * Downloads a portion of a media image given the ref previously acquired.
   *
   * @param ref    the ref acquired by called `fetchMediaImageRefs`.
   * @param start  the start byte {inclusive} of the media image region.
   * @param length the length (in bytes) of the media image region.
   * @return The memory region requested.
   */
  byte[] fetchMediaImageRegion(MediaImageRef ref, int start, int length) throws ApiException;

  /**
   * Uploads a new system state.
   *
   * @return A unique token that can be used to fetch this state later.
   */
  long uploadState(SystemState state) throws ApiException;

  /**
   * Fetches a system state associated with the given token.
   *
   * @param token the token of the state to download. Was previously returned
   *              when a state was uploaded
   */
  SystemState downloadState(long token) throws ApiException;

  /**
   * Fetches a system state associated with the given token.
   *
   * @param token                      the token of the state to download.
   *                                   Was previously returned
   *                                   when a state was uploaded
   * @param exclude_memory_region_Data whether to exclude sending down the
   *                                   'data' portion of the memory regions
   *                                   returned. This is useful for systems
   *                                   with little memory. Use
   *                                   `downloadSystemStateMemoryRegion`
   *                                   instead to fetch any sized slices.
   */
  SystemState downloadState(long token, boolean exclude_memory_region_Data) throws ApiException;

  /**
   * Downloads a portion of a system state's memory region.
   *
   * @param token  the system of the state that's memory region should be
   *               fetched.
   * @param start  the start address (inclusive) of the memory region.
   * @param length the length of the memory region to fetch.
   * @return The memory region requested.
   */
  byte[] downloadSystemStateMemoryRegion(long token, int start, int length) throws ApiException;
}
