package curly.octo.map.generators.kiss;

import java.util.ArrayList;

public class GoxelExportJson {
    public int width;
    public int height;
    public int depth;
    public int[] minPos;
    public ArrayList<GoxelSlice> slices;

    public static class GoxelSlice {
        public int slice;  // The Y-level index (horizontal slice)
        public ArrayList<GoxelPixel> pixels;
    }

    public static class GoxelPixel {
        public int x;
        public int z;
        public int r;
        public int g;
        public int b;
        public int a;
    }
}
