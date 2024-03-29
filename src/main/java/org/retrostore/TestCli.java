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
import org.retrostore.client.common.proto.MediaImageRef;
import org.retrostore.client.common.proto.MediaType;
import org.retrostore.client.common.proto.SystemState;
import org.retrostore.client.common.proto.Trs80Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
      new FetchMediaImageRefsTest(),
      new FetchMediaImageRangeTest(),
      new UploadAndDownloadStateTest(),
      new UploadBadMemoryRegionsStateTest(),
      new ExcludeMemoryRegionDataDownloadSystemStateTest(),
      new DownloadStateMemoryRegionsTests()
  };

  public static void main(String[] args) throws ApiException {
    RetrostoreClientImpl retrostore =
        RetrostoreClientImpl.get("n/a", "https://retrostore.org/api/%s", false);
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
          System.out.println("\u001b[32mTEST PASSED: " + test.getClass().getSimpleName() + "\u001B[0m");
          System.out.println("=========================================");
        } else {
          System.out.println("\u001B[31mTEST FAILED: " + test.getClass().getSimpleName() + "\u001B[0m");
          System.out.println("=========================================");
        }
      } catch (Exception ex) {
        System.err.println("\u001B[31mTEST FAILED: " + test.getClass().getSimpleName() + "\u001B[0m");
        System.err.println("Exception: " + ex.getMessage() + (ex.getCause() != null ? " -> " + ex.getCause().getMessage() : ""));
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
      // TODO: We should use a non-existent test model to test these without
      //  interfering with real
      // data.
      ImmutableSet<MediaType> mediaTypes = ImmutableSet.of(type);
      List<App> apps = retrostore.fetchApps(0, 50, "", mediaTypes);
      for (App app : apps) {
        System.out.printf(
            "App %s (%s) has image of type %s.%n", app.getName(), app.getId()
            , type.name());
        boolean hasImage =
            hasImageOfType(retrostore.fetchMediaImages(app.getId()), type);
        if (!hasImage) {
          System.err.printf(
              "App '%s' with ID %s has no image of type %s%n",
              app.getName(), app.getId(), type.name());
          return false;
        }
      }
      return true;
    }

    private boolean hasImageOfType(List<MediaImage> mediaImages,
                                   MediaType type) {
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
          System.err.println("Item with ID " + item.getId() + " has bad " +
              "release year");
          error = true;
        }

        List<MediaImage> mediaImages =
            retrostore.fetchMediaImages(item.getId());
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
          System.err.println("Item with ID " + item.getId() + " has bad " +
              "release year");
          error = true;
        }


        List<MediaImage> mediaImages =
            retrostore.fetchMediaImages(item.getId());
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
      List<MediaImage> mediaImages =
          retrostore.fetchMediaImages(DANCING_DEMON_ID);

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

    private MediaImage getImageOfType(List<MediaImage> mediaImages,
                                      MediaType type) {
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
              "Order is not correct: %s > %s\n[%s]%n", name1, name2,
              Joiner.on(",").join(appNames));
          return false;
        }
      }
      return true;
    }
  }

  static class ExcludeMemoryRegionDataDownloadSystemStateTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState uploadState = createRandomState();
      long token = retrostore.uploadState(uploadState);
      SystemState downloadState = retrostore.downloadState(token, true);

      // First lets verify that the upload state had memory regions.
      if (uploadState.getMemoryRegionsList().isEmpty()) {
        System.err.println("Internal error: Upload state should have memory " +
            "regions");
        return false;
      }

      // Then verify that the downloaded state has the same number of memory
      // regions.
      if (downloadState.getMemoryRegionsCount() !=
          uploadState.getMemoryRegionsCount()) {
        System.err.println("Downloaded state should have the same number of " +
            "memory regions.");
        return false;
      }

      // Make sure none of the regions have data
      if (downloadState.getMemoryRegionsList()
          .stream()
          .anyMatch(r -> r.getData().size() > 0)) {
        System.err.println("At least one memory region has data.");
        return false;
      }
      return true;
    }
  }

  static class UploadAndDownloadStateTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState buildState = createRandomState();
      long startTime = System.currentTimeMillis();
      long token = retrostore.uploadState(buildState);
      System.out.printf("Uploading state took: %d ms\n",
          System.currentTimeMillis() - startTime);
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

  static class UploadBadMemoryRegionsStateTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState.Builder state = createRandomState().toBuilder();
      SystemState.MemoryRegion.Builder badRegion =
          state.getMemoryRegions(0).toBuilder().setStart(-10);
      state.addMemoryRegions(badRegion);
      try {
        retrostore.uploadState(state.build());
      } catch (ApiException ex) {
        // expected
        return true;
      }
      return false;
    }
  }

  static class DownloadStateMemoryRegionsTests implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      SystemState.Builder state =
          createRandomState().toBuilder().clearMemoryRegions();
      // Non-overlapping separate region.
      state.addMemoryRegions(SystemState.MemoryRegion.newBuilder()
          .setStart(1000)
          .setData(ByteString.copyFrom(new byte[]{42, 43, 44, 45}))
          .setLength(4));

      // Two regions that are connecting.
      state.addMemoryRegions(SystemState.MemoryRegion.newBuilder()
          .setStart(1100)
          .setData(ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))
          .setLength(8));
      state.addMemoryRegions(SystemState.MemoryRegion.newBuilder()
          .setStart(1108)
          .setData(ByteString.copyFrom(new byte[]{11, 22, 33, 44, 55, 66}))
          .setLength(6));
      // A region that is close by the former, but with a gap.
      state.addMemoryRegions(SystemState.MemoryRegion.newBuilder()
          .setStart(1120)
          .setData(ByteString.copyFrom(new byte[]{101, 102, 103, 104, 105}))
          .setLength(5));
      long token = retrostore.uploadState(state.build());
      System.out.printf("Uploaded test state and got token '%d'\n", token);

      if (token <= 0) {
        System.err.printf("Got non-positive token %d\n", token);
        return false;
      }

      // Exact match of uploads
      if (!checkMemoryEqual(1,
          retrostore.downloadSystemStateMemoryRegion(token, 1000, 4),
          new byte[]{42, 43, 44, 45}))
        return false;
      if (!checkMemoryEqual(2,
          retrostore.downloadSystemStateMemoryRegion(token, 1108, 6),
          new byte[]{11, 22, 33, 44, 55, 66}))
        return false;

      // Requesting two connected regions at once..
      if (!checkMemoryEqual(3,
          retrostore.downloadSystemStateMemoryRegion(token, 1100, 14),
          new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 11, 22, 33, 44, 55, 66}))
        return false;


      // Requesting more (padding) should result in '0'.
      if (!checkMemoryEqual(4,
          retrostore.downloadSystemStateMemoryRegion(token, 998, 8),
          new byte[]{0, 0, 42, 43, 44, 45, 0, 0}))

        return false;

      // Request half into one.
      if (!checkMemoryEqual(5,
          retrostore.downloadSystemStateMemoryRegion(token, 1002, 4),
          new byte[]{44, 45, 0, 0}))
        return false;

      // Request half into one across and half into another region.
      if (!checkMemoryEqual(6,
          retrostore.downloadSystemStateMemoryRegion(token, 1111, 12),
          new byte[]{44, 55, 66, 0, 0, 0, 0, 0, 0, 101, 102, 103}))
        return false;

      // Request region completely inside another.
      if (!checkMemoryEqual(7,
          retrostore.downloadSystemStateMemoryRegion(token, 1122, 2),
          new byte[]{103, 104}))
        return false;
      return true;
    }
  }

  private static boolean checkMemoryEqual(int n, byte[] actual, byte[] want) {
    if (!Arrays.equals(actual, want)) {
      System.err.printf("#%d Memory does not match: %s vs %s\n",
          n, Arrays.toString(actual), Arrays.toString(want));
      return false;
    }
    return true;
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
    int[] froms = (new Random()).ints(10, 0, 32000).map(Math::abs).toArray();
    for (int from : froms) {
      byte[] data = new byte[32000];
      (new Random()).nextBytes(data);
      for (int i = 0; i < data.length; ++i) {
        data[i] = (byte) Math.abs(data[i]);
      }
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
        System.err.println("'Breakdown' should have exactly two media images " +
            "but got: " + mediaImages.size());
        return false;
      }

      Set<MediaType> imageTypes =
          mediaImages.stream().map(MediaImage::getType).collect(Collectors.toSet());

      if (!imageTypes.equals(Set.of(MediaType.COMMAND, MediaType.DISK))) {
        System.err.println("Media image types not correct.");
        return false;
      }


      // Let's say we only want the CMD.
      mediaImages = retrostore.fetchMediaImages(BREAKDOWN_ID,
          Set.of(MediaType.COMMAND));
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

  static class FetchMediaImageRefsTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      String BREAKDOWN_ID = "29b20252-680f-11e8-b4a9-1f10b5491ef5";
      // Breakdown has a "Disk" and "cmd" image.
      List<MediaImage> mediaImages = retrostore.fetchMediaImages(BREAKDOWN_ID);
      List<MediaImageRef> mediaImageRefs =
          retrostore.fetchMediaImageRefs(BREAKDOWN_ID);

      Map<String, Integer> fileSizes =
          mediaImages.stream().collect(Collectors.toMap(MediaImage::getFilename, m -> m.getData().size()));

      if (mediaImageRefs.size() != 2) {
        System.err.println("'Breakdown' should have exactly two media images " +
            "but got: " + mediaImageRefs.size());
        return false;
      }

      // Assert that the file sizes match when getting refs.
      for (MediaImageRef ref : mediaImageRefs) {
        boolean error = false;
        System.out.println("Media Image Ref received: " + ref.getToken());
        if (ref.getSize() != fileSizes.get(ref.getFilename())) {
          System.err.println("File size for " + ref.getFilename() + " does " +
              "not match: " + ref.getSize() + " vs " + fileSizes.get(ref.getFilename()));
          error = true;
        }
        if (error)
          return false;
      }


      Set<MediaType> imageTypes =
          mediaImageRefs.stream().map(MediaImageRef::getType).collect(Collectors.toSet());

      if (!imageTypes.equals(Set.of(MediaType.COMMAND, MediaType.DISK))) {
        System.err.println("Media image types not correct.");
        return false;
      }

      Set<String> mediaRefs =
          mediaImageRefs.stream().map(MediaImageRef::getToken).collect(Collectors.toSet());
      // FYI: In the API docs we say that these references might not live
      // long. Currently
      //      they do, but we might change this in the future.
      Set<String> wantedRefs = Set.of(
          "29b20252-680f-11e8-b4a9-1f10b5491ef5/disk_0.dsk",
          "29b20252-680f-11e8-b4a9-1f10b5491ef5/command.CMD");
      if (!mediaRefs.equals(wantedRefs)) {
        System.err.println("Received referenced do not match.");
        return false;
      }


      // Let's say we only want the CMD.
      mediaImageRefs = retrostore.fetchMediaImageRefs(BREAKDOWN_ID,
          Set.of(MediaType.COMMAND));
      if (mediaImageRefs.size() != 1) {
        System.err.println("Requested only the CMD, but got more!");
        return false;
      }
      if (mediaImageRefs.get(0).getType() != MediaType.COMMAND) {
        System.err.println("Requested a CMD but got something else.");
        return false;
      }
      return true;
    }
  }

  static class FetchMediaImageRangeTest implements RetroStoreApiTest {

    @Override
    public boolean runTest(RetrostoreClient retrostore) throws ApiException {
      String BREAKDOWN_ID = "29b20252-680f-11e8-b4a9-1f10b5491ef5";
      // Breakdown has a "Disk" and "cmd" image.
      List<MediaImage> mediaImages = retrostore.fetchMediaImages(BREAKDOWN_ID);
      List<MediaImageRef> mediaImageRefs =
          retrostore.fetchMediaImageRefs(BREAKDOWN_ID);

      if (mediaImages.isEmpty() || mediaImages.size() != mediaImageRefs.size()) {
        System.err.println("Setup issue: Mediaimages either empty or size " +
            "mismatch!");
        return false;
      }

      // Get the actual and complete ground truth of the data.
      Map<String, byte[]> mediaBytes =
          mediaImages.stream().collect(Collectors.toMap(MediaImage::getFilename, m -> m.getData().toByteArray()));


      // First make sure that we can fetch the whole image and it matches.
      for (MediaImageRef ref : mediaImageRefs) {
        System.out.println("Media Image Ref received: " + ref.getToken());
        byte[] bytes = retrostore.fetchMediaImageRegion(ref, 0, ref.getSize());
        System.out.println("Region received.");
        if (!Arrays.equals(bytes, mediaBytes.get(ref.getFilename()))) {
          System.err.println("Full region does not match for token " + ref.getToken());
          return false;
        }
      }

      // Test a few sub-regions
      MediaImageRef ref = mediaImageRefs.get(0);
      String filename = ref.getFilename();
      byte[] truth = mediaBytes.get(filename);
      int trueSize = truth.length;

      // Start region
      {
        byte[] want = Arrays.copyOfRange(truth, 0, 42);
        byte[] bytes = retrostore.fetchMediaImageRegion(ref, 0, 42);
        if (!Arrays.equals(bytes, want)) {
          System.err.println("Start region does no match");
          return false;
        }
      }

      // End region
      {
        byte[] want = Arrays.copyOfRange(truth, trueSize - 42, trueSize);
        byte[] bytes = retrostore.fetchMediaImageRegion(ref, trueSize - 42, 42);
        if (!Arrays.equals(bytes, want)) {
          System.err.println("End region does no match");
          return false;
        }
      }

      // A random region in the middle.
      {
        byte[] want = Arrays.copyOfRange(truth, 4242, 4242 + 1234);
        byte[] bytes = retrostore.fetchMediaImageRegion(ref, 4242, 1234);
        if (!Arrays.equals(bytes, want)) {
          System.err.println("End region does no match");
          return false;
        }
      }
      return true;
    }
  }

  interface RetroStoreApiTest {
    boolean runTest(RetrostoreClient retrostore) throws ApiException;
  }
}
