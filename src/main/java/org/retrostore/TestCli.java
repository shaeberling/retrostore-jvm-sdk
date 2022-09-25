/*
 * Copyright 2017, Sascha Häberling
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.retrostore;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import org.retrostore.client.common.proto.App;
import org.retrostore.client.common.proto.AppNano;
import org.retrostore.client.common.proto.MediaImage;
import org.retrostore.client.common.proto.MediaType;
import org.retrostore.client.common.proto.SystemState;
import org.retrostore.client.common.proto.Trs80Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A CLI to test the RetroStore API.
 */
public class TestCli {

  private static final RetroStoreApiTest[] tests = {
      new FetchMultipleNanoTest(),
      new FetchMultipleNanoQueryWithoutTypes(),
      new FetchMultipleNanoQueryWithTypes(),
      new FetchMultipleTest(),
      new FetchSingleTest(),
      new FilterByMediaTypeTest(),
      new BasicFileTypeTest(),
      new SortTest(),
      new FetchMediaImagesTest(),
      new UploadAndDownloadStateTest(),
      new ExcludeMemoryRegionsDownloadSystemStateTest()
  };

  public static void main(String[] args) throws ApiException {
    RetrostoreClientImpl retrostore =
        RetrostoreClientImpl.get("n/a", "https://retrostore.org/api/%s",
            false);
    if (args.length > 1 && args[0].equalsIgnoreCase("--search")) {
      StringBuilder query = new StringBuilder();
      for (int i = 1; i < args.length; ++i) {
        query.append(args[i]).append(" ");
      }
      searchApps(retrostore, query.toString().trim());
      return;
    }

    System.out.println("Testing the RetroStoreClient.");
    int success = 0;
    for (RetroStoreApiTest test : tests) {
      try {
        if (test.runTest(retrostore)) {
          success++;
          System.out.println("TEST PASSED: " + test.getClass().getSimpleName());
          System.out.println("=========================================");
        } else {
          System.out.println("TEST FAILED: " + test.getClass().getSimpleName());
          System.out.println("=========================================");
        }
      } catch (Exception ex) {
        System.err.println("TEST FAILED: " + test.getClass().getSimpleName());
        System.err.println("Exception: " + ex.getMessage());
        System.out.println("=========================================");
      }
    }
    System.out.println("*****************************************");
    System.out.printf("%d out of %d tests passed.%n", success, tests.length);
    System.out.println("*****************************************");
  }

  static void searchApps(RetrostoreClientImpl retrostore, String query) throws ApiException {
    System.out.printf("Searching RetroStore for '%s'.%n", query);
    List<App> apps = retrostore.fetchApps(1, 1, query, null);
    for (App app : apps) {
      System.out.printf("Result: [%s] %s%n", app.getId(), app.getName());
    }
  }

  /**
   * Tests the has-media-type filter option.
   */
  static class FilterByMediaTypeTest implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      if (!testForType(MediaType.COMMAND, retrostore)) {
        System.err.println("Failed for media type COMMAND");
        return false;
      }
      if (!testForType(MediaType.DISK, retrostore)) {
        System.err.println("Failed for media type DISK");
        return false;
      }
      if (!testForType(MediaType.CASSETTE, retrostore)) {
        System.err.println("Failed for media type COMMAND");
        return false;
      }
      return true;
    }

    private boolean testForType(MediaType type, RetrostoreClient retrostore) throws ApiException {
      // TODO: We should use a non-existent test model to test these without interfering with real
      // data.
      ImmutableSet<MediaType> mediaTypes = ImmutableSet.of(type);
      List<App> apps = retrostore.fetchApps(0, 50, "", mediaTypes);
      for (App app : apps) {
        System.out.printf(
            "App %s (%s) has image of type %s.%n", app.getName(), app.getId(), type.name());
        boolean hasImage = hasImageOfType(retrostore.fetchMediaImages(app.getId()), type);
        if (!hasImage) {
          System.err.printf(
              "App '%s' with ID %s has no image of type %s%n",
              app.getName(), app.getId(), type.name());
          return false;
        }
      }
      return true;
    }

    private boolean hasImageOfType(List<MediaImage> mediaImages, MediaType type) {
      for (MediaImage mediaImage : mediaImages) {
        if (mediaImage.getType() == type) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Test the basic fetch function.
   */
  static class FetchMultipleTest implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      List<App> items = retrostore.fetchApps(0, 5);

      boolean error = false;
      for (App item : items) {
        System.out.println(item.getName() + " - " + item.getId());
        if (item.getId().isBlank()) {
          System.err.println("Item with name " + item.getName() + " has no ID");
          error = true;
        }
        if (item.getName().isBlank()) {
          System.err.println("Item with ID " + item.getId() + " has no name");
          error = true;
        }
        if (item.getAuthor().isBlank()) {
          System.err.println("Item with ID " + item.getId() + " has no author");
          error = true;
        }
        if (item.getReleaseYear() < 1900) {
          System.err.println("Item with ID " + item.getId() + " has bad release year");
          error = true;
        }

        List<MediaImage> mediaImages = retrostore.fetchMediaImages(item.getId());
        for (MediaImage mediaImage : mediaImages) {
          System.out.printf(
              "- Media: %s, %s, %d%n",
              mediaImage.getFilename(),
              mediaImage.getType().name(),
              mediaImage.getData().size());
        }
      }


      if (error) {
        return false;
      }

      if (items.size() == 5) {
        System.out.printf("Got %d items.%n", items.size());
        return true;
      } else {
        System.err.printf("Got %d but expected %d%n", items.size(), 5);
        return false;
      }
    }
  }

  static class FetchMultipleNanoTest implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      List<AppNano> items = retrostore.fetchAppsNano(0, 5);

      boolean error = false;
      for (AppNano item : items) {
        System.out.println(item.getName() + " - " + item.getId());
        if (item.getId().isBlank()) {
          System.err.println("Item with name " + item.getName() + " has no ID");
          error = true;
        }
        if (item.getName().isBlank()) {
          System.err.println("Item with ID " + item.getId() + " has no name");
          error = true;
        }
        if (item.getAuthor().isBlank()) {
          System.err.println("Item with ID " + item.getId() + " has no author");
          error = true;
        }
        if (item.getReleaseYear() < 1900) {
          System.err.println("Item with ID " + item.getId() + " has bad release year");
          error = true;
        }


        List<MediaImage> mediaImages = retrostore.fetchMediaImages(item.getId());
        for (MediaImage mediaImage : mediaImages) {
          System.out.printf(
              "- Media: %s, %s, %d%n",
              mediaImage.getFilename(),
              mediaImage.getType().name(),
              mediaImage.getData().size());
        }
      }

      if (error) {
        return false;
      }

      if (items.size() == 5) {
        System.out.printf("Got %d items.%n", items.size());
        return true;
      } else {
        System.err.printf("Got %d but expected %d%n", items.size(), 5);
        return false;
      }
    }
  }

  static class FetchMultipleNanoQueryWithoutTypes implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      Set<String> appNamesNoFilter = retrostore.fetchAppsNano(
              0, 10, "ldos OR donkey", null).stream()
          .map(AppNano::getName).collect(Collectors.toSet());

      if (!appNamesNoFilter.contains("Donkey Kong")) {
        System.err.println("Donkey Kong is mising.");
        return false;
      }
      if (!appNamesNoFilter.contains("LDOS - Model I")) {
        System.err.println("LDOS - Model I is mising.");
        return false;
      }
      if (!appNamesNoFilter.contains("LDOS - Model III")) {
        System.err.println("LDOS - Model III is mising.");
        return false;
      }

      return true;
    }
  }


  static class FetchMultipleNanoQueryWithTypes implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      Set<String> appNamesNoFilter = retrostore.fetchAppsNano(
          0, 10, "ldos OR donkey",
          Set.of(MediaType.COMMAND)).stream()
          .map(AppNano::getName).collect(Collectors.toSet());

      if (!appNamesNoFilter.contains("Donkey Kong")) {
        System.err.println("Donkey Kong is mising.");
        return false;
      }
      if (appNamesNoFilter.contains("LDOS - Model I")) {
        System.err.println("LDOS - Model I should not be returned.");
        return false;
      }
      if (appNamesNoFilter.contains("LDOS - Model III")) {
        System.err.println("LDOS - Model III should not be returned.");
        return false;
      }

      return true;
    }
  }


  static class FetchSingleTest implements RetroStoreApiTest {
    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      final String DONKEY_KONG_ID = "a2729dec-96b3-11e7-9539-e7341c560175";
      App app = retrostore.getApp(DONKEY_KONG_ID);

      if (app == null) {
        System.err.println("Cannot find any app with that key");
        return false;
      }
      if (!app.getId().equals(DONKEY_KONG_ID)) {
        System.err.println("Returned app has the wrong key.");
        return false;
      }
      if (!app.getName().equals("Donkey Kong")) {
        System.err.println("App's name does not match.");
        return false;
      }
      return true;
    }
  }

  static class BasicFileTypeTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      final String DANCING_DEMON_ID = "faf29c58-f05b-11e8-81f8-fbefaef24896";
      List<MediaImage> mediaImages = retrostore.fetchMediaImages(DANCING_DEMON_ID);

      if (mediaImages.isEmpty()) {
        System.err.println("No media images found for Dancing Demon");
        return false;
      }
      MediaImage basicImage = getImageOfType(mediaImages, MediaType.BASIC);
      if (basicImage == null) {
        System.err.println("No BASIC image found for Dancing Demon.");
        return false;
      }

      if (Strings.isNullOrEmpty(basicImage.getFilename())) {
        System.err.println("BASIC image has no filename.");
        return false;
      }
      if (basicImage.getData().isEmpty()) {
        System.err.println("BASIC image has no data.");
        return false;
      }
      return true;
    }

    private MediaImage getImageOfType(List<MediaImage> mediaImages, MediaType type) {
      for (MediaImage mediaImage : mediaImages) {
        if (mediaImage.getType() == type) {
          return mediaImage;
        }
      }
      return null;
    }
  }

  static class SortTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      // Fetch apps through paging and add their names in order to this list.
      List<String> appNames = new ArrayList<>();
      System.out.print("Fetching apps through paging..");
      try {
        while (true) {
          System.out.print(".");
          retrostore.fetchApps(appNames.size(), 2).forEach(a -> appNames.add(a.getName()));
        }
      } catch (ApiException ignore) {
        // Thrown when start is out of range.
      }
      System.out.println(". Done");

      if (appNames.isEmpty()) {
        System.err.println("No apps fetched");
        return false;
      }

      // Ensure that the order is correct throughout the whole list.
      for (int i = 1; i < appNames.size(); ++i) {
        String name1 = appNames.get(i - 1);
        String name2 = appNames.get(i);
        if (name1.compareTo(name2) > 0) {
          System.err.printf(
              "Order is not correct: %s > %s\n[%s]%n", name1, name2, Joiner.on(",").join(appNames));
          return false;
        }
      }
      return true;
    }
  }

  static class ExcludeMemoryRegionsDownloadSystemStateTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState uploadState = createRandomState();
      long token = retrostore.uploadState(uploadState);
      SystemState downloadState = retrostore.downloadState(token, true);

      // First lets verify that the upload state had memory regions.
      if (uploadState.getMemoryRegionsList().isEmpty()) {
        System.err.println("Internal error: Upload state should have memory regions");
        return false;
      }

      // Then verify that the downloaded state has no memory regions.
      if (!downloadState.getMemoryRegionsList().isEmpty()) {
        System.err.println("Downloaded state should have no memory regions.");
        return false;
      }

      // Make sure the rest of the object is the same
      SystemState uploadNoMemory =
          uploadState.toBuilder().clearMemoryRegions().build();
      if (!uploadNoMemory.equals(downloadState)) {
        System.err.println("Downloaded state does not match expecations");
        return false;
      }

      return true;
    }
  }

  static class UploadAndDownloadStateTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState buildState = createRandomState();
      long token = retrostore.uploadState(buildState);
      System.out.printf("Uploaded test state and got token '%d'\n", token);

      if (token <= 0) {
        System.err.printf("Got non-positive token %d\n", token);
        return false;
      }

      SystemState downloadedState = retrostore.downloadState(token);
      if (!downloadedState.equals(buildState)) {
        System.err.println("Downloaded state does not match sent state.");
        return false;
      }

      return true;
    }
  }

  private static SystemState createRandomState() {
    // First, upload a test SystemState.
    SystemState.Builder state = SystemState.newBuilder();

    state.setModel(Trs80Model.MODEL_III);
    SystemState.Registers.Builder regs = SystemState.Registers.newBuilder();
    regs.setIx(9);
    regs.setIy(7);
    regs.setPc(5);
    regs.setSp(3);
    regs.setAf(1);
    regs.setBc(2);
    regs.setDe(4);
    regs.setHl(6);
    regs.setAfPrime(100);
    regs.setBcPrime(80);
    regs.setDePrime(42);
    regs.setHlPrime(23);
    regs.setI(11);
    regs.setR1(22);
    regs.setR2(200);
    state.setRegisters(regs);

    // Add random data for memory regions.
    int[] froms = (new Random()).ints(10).toArray();
    for (int from : froms) {
      byte[] data = new byte[32000];
      (new Random()).nextBytes(data);
      SystemState.MemoryRegion.Builder region =
          SystemState.MemoryRegion.newBuilder();
      region.setStart(from);
      region.setLength(32000);
      region.setData(ByteString.copyFrom(data));
      state.addMemoryRegions(region);
    }

    return state.build();
  }

  static class FetchMediaImagesTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      String BREAKDOWN_ID = "29b20252-680f-11e8-b4a9-1f10b5491ef5";
      // Breakdown has a "Disk" and "cmd" image.
      List<MediaImage> mediaImages = retrostore.fetchMediaImages(BREAKDOWN_ID);

      if (mediaImages.size() != 2) {
        System.err.println("'Breakdown' should have exactly two media images but got: " + mediaImages.size());
        return false;
      }

      Set<MediaType> imageTypes =
          mediaImages.stream().map(MediaImage::getType).collect(Collectors.toSet());

      if (!imageTypes.equals(Set.of(MediaType.COMMAND, MediaType.DISK))) {
        System.err.println("Media image types not correct.");
        return false;
      }


      // Let's say we only want the CMD.
      mediaImages = retrostore.fetchMediaImages(BREAKDOWN_ID, Set.of(MediaType.COMMAND));
      if (mediaImages.size() != 1) {
        System.err.println("Requested only the CMD, but got more!");
        return false;
      }
      if (mediaImages.get(0).getType() != MediaType.COMMAND) {
        System.err.println("Requested a CMD but got something else.");
        return false;
      }

      return true;
    }
  }

  interface RetroStoreApiTest {
    boolean runTest(RetrostoreClient retrostore) throws ApiException;
  }
}
