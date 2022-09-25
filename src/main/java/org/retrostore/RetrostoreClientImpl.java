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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.retrostore.client.common.FetchMediaImagesApiParams;
import org.retrostore.client.common.GetAppApiParams;
import org.retrostore.client.common.ListAppsApiParams;
import org.retrostore.client.common.proto.ApiResponseApps;
import org.retrostore.client.common.proto.ApiResponseAppsNano;
import org.retrostore.client.common.proto.ApiResponseDownloadSystemState;
import org.retrostore.client.common.proto.ApiResponseMediaImages;
import org.retrostore.client.common.proto.ApiResponseUploadSystemState;
import org.retrostore.client.common.proto.App;
import org.retrostore.client.common.proto.AppNano;
import org.retrostore.client.common.proto.DownloadSystemStateMemoryRegionParams;
import org.retrostore.client.common.proto.DownloadSystemStateParams;
import org.retrostore.client.common.proto.FetchMediaImagesParams;
import org.retrostore.client.common.proto.GetAppParams;
import org.retrostore.client.common.proto.ListAppsParams;
import org.retrostore.client.common.proto.MediaImage;
import org.retrostore.client.common.proto.MediaType;
import org.retrostore.client.common.proto.SystemState;
import org.retrostore.client.common.proto.UploadSystemStateParams;
import org.retrostore.net.UrlFetcher;
import org.retrostore.net.UrlFetcherImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class RetrostoreClientImpl implements RetrostoreClient {
  private static final String DEFAULT_SERVER_URL = "https://retrostore.org/api/%s";
  private static final boolean DEFAULT_GZIP_ENABLED = false;

  private final String mApiKey;
  private final String mServerUrl;
  private final UrlFetcher mUrlFetcher;
  private final Executor mExecutor;

  RetrostoreClientImpl(String apiKey,
                       String serverUrl,
                       boolean enableGzip,
                       UrlFetcher urlFetcher,
                       Executor executor) {
    mApiKey = apiKey;
    mServerUrl = serverUrl;
    mUrlFetcher = urlFetcher;
    mExecutor = executor;
  }

  public static RetrostoreClientImpl getDefault(String apiKey) {
    return new RetrostoreClientImpl(apiKey, DEFAULT_SERVER_URL, DEFAULT_GZIP_ENABLED, new
        UrlFetcherImpl(), Executors.newSingleThreadExecutor());
  }

  @SuppressWarnings("WeakerAccess") // This is the public API.
  public static RetrostoreClientImpl get(String apiKey, String serverUrl, boolean enableGzip) {
    // Use default URL fetcher and executor.
    return new RetrostoreClientImpl(apiKey, serverUrl, enableGzip, new UrlFetcherImpl(),
        Executors.newSingleThreadExecutor());
  }

  @Override
  public App getApp(String appId) throws ApiException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "appId missing.");
    return getAppInternal(GetAppParams.newBuilder().setAppId(appId).build());
  }

  /** Note: Testing legacy JSON code path for older clients. */
  @Deprecated
  App getAppOld(String appId) throws ApiException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "appId missing.");
    return getAppInternal(new GetAppApiParams(appId));
  }

  private App getAppInternal(Object params) throws ApiException {
    String url = String.format(mServerUrl, "getApp");
    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseApps apiResponse = ApiResponseApps.parseFrom(content);

      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      if (apiResponse.getAppList().size() > 0) {
        return apiResponse.getAppList().get(0);
      } else {
        return null;
      }
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }

  @Override
  public List<App> fetchApps(int start, int num) throws ApiException {
    return fetchAppsInternal(ListAppsParams.newBuilder().setStart(start).setNum(num).build());
  }

  /** Note: Testing legacy JSON code path for older clients. */
  @Deprecated
  List<App> fetchAppsOld(int start, int num) throws ApiException {
    return fetchAppsInternal(new ListAppsApiParams(start, num));
  }

  @Override
  public List<App> fetchApps(int start, int num, String searchQuery, Set<MediaType> hasMediaTypes)
      throws ApiException {
    if (hasMediaTypes == null) {
      hasMediaTypes = new HashSet<>();
    }

    return fetchAppsInternal(
        ListAppsParams.newBuilder()
            .setStart(start)
            .setNum(num)
            .setQuery(searchQuery)
            .setTrs80(ListAppsParams.Trs80Params.newBuilder().addAllMediaTypes(hasMediaTypes))
            .build());
  }

  @Override
  public List<AppNano> fetchAppsNano(int start, int num) throws ApiException {
    ListAppsParams params = ListAppsParams.newBuilder()
        .setStart(start)
        .setNum(num)
        .build();
    return fetchAppsNanoInternal(params);
  }

  @Override
  public List<AppNano> fetchAppsNano(int start, int num, String searchQuery,
                                     Set<MediaType> hasMediaTypes) throws ApiException {
    if (hasMediaTypes == null) {
      hasMediaTypes = new HashSet<>();
    }
    ListAppsParams params = ListAppsParams.newBuilder()
        .setStart(start)
        .setNum(num)
        .setQuery(searchQuery)
        .setTrs80(ListAppsParams.Trs80Params.newBuilder().addAllMediaTypes(hasMediaTypes))
        .build();
    return fetchAppsNanoInternal(params);
  }

  private List<AppNano> fetchAppsNanoInternal(ListAppsParams params) throws ApiException {
    String url = String.format(mServerUrl, "listAppsNano");

    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseAppsNano apiResponse = ApiResponseAppsNano.parseFrom(content);

      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      return apiResponse.getAppList();
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }


  /** Note: Testing legacy JSON code path for older clients. */
  @Deprecated
  List<App> fetchAppsOld(int start, int num, String searchQuery, Set<MediaType> hasMediaTypes)
      throws ApiException {
    if (hasMediaTypes == null) {
      hasMediaTypes = new HashSet<>();
    }
    List<String> mediaTypes = new ArrayList<>(hasMediaTypes.size());
    for (MediaType mediaType : hasMediaTypes) {
      mediaTypes.add(mediaType.name());
    }
    return fetchAppsInternal(new ListAppsApiParams(start, num, searchQuery, mediaTypes));
  }

  private List<App> fetchAppsInternal(Object params) throws ApiException {
    String url = String.format(mServerUrl, "listApps");
    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseApps apiResponse = ApiResponseApps.parseFrom(content);

      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      return apiResponse.getAppList();
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }

  @Override
  public List<MediaImage> fetchMediaImages(String appId) throws ApiException {
    return fetchMediaImages(appId, new HashSet<>());
  }

  @Override
  public List<MediaImage> fetchMediaImages(String appId, Set<MediaType> types) throws ApiException {
    List<MediaImage> images =
        fetchMediaImagesInternal(FetchMediaImagesParams
        .newBuilder()
        .addAllMediaType(types)
        .setAppId(appId)
        .build());

    // Only return non-zero images, which is skip zero-size "UNKNOWN" entries.
    return images.stream()
        .filter(img -> img.getData().size() > 0)
        .collect(Collectors.toList());
  }

  /** Note: Testing legacy JSON code path for older clients. */
  @Deprecated
  List<MediaImage> fetchMediaImagesOld(String appId) throws ApiException {
    return fetchMediaImagesInternal(new FetchMediaImagesApiParams(appId));
  }

  private List<MediaImage> fetchMediaImagesInternal(Object params) throws ApiException {
    String url = String.format(mServerUrl, "fetchMediaImages");
    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseMediaImages apiResponse = ApiResponseMediaImages.parseFrom(content);

      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      return apiResponse.getMediaImageList();
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }

  @Override
  public long uploadState(SystemState state) throws ApiException {
    UploadSystemStateParams params = UploadSystemStateParams.newBuilder().setState(state).build();
    String url = String.format(mServerUrl, "uploadState");

    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseUploadSystemState apiResponse = ApiResponseUploadSystemState.parseFrom(content);
      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      return apiResponse.getToken();
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }

  @Override
  public SystemState downloadState(long token) throws ApiException {
    return downloadState(token, false);
  }

  @Override
  public SystemState downloadState(long token,
                                   boolean exclude_memory_regions)
      throws ApiException {
    DownloadSystemStateParams params =
        DownloadSystemStateParams.newBuilder()
            .setToken(token)
            .setExcludeMemoryRegions(exclude_memory_regions)
            .build();
    String url = String.format(mServerUrl, "downloadState");

    try {
      byte[] content = mUrlFetcher.fetchUrl(url, params);
      ApiResponseDownloadSystemState apiResponse =
          ApiResponseDownloadSystemState.parseFrom(content);

      if (!apiResponse.getSuccess()) {
        throw new ApiException(String.format(
            "Server reported error: '%s'", apiResponse.getMessage()));
      }
      return apiResponse.getSystemState();
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }

  @Override
  public byte[] downloadSystemStateMemoryRegion(long token,
                                                int start,
                                                int length) throws ApiException {
    DownloadSystemStateMemoryRegionParams params =
        DownloadSystemStateMemoryRegionParams.newBuilder()
            .setToken(token)
            .setStart(start)
            .setLength(length)
            .build();
    String url = String.format(mServerUrl, "downloadStateMemoryRegion");

    try {
      byte[] bytes = mUrlFetcher.fetchUrl(url, params);
      if (bytes.length != params.getLength()) {
        throw new ApiException(String.format("Length received (%d) does not " +
            "match length requested (%d)", bytes.length, params.getLength()));
      }
      return bytes;
    } catch (IOException e) {
      throw new ApiException("Unable to make request to server.", e);
    }
  }
}
