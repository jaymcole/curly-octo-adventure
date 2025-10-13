// Goxel Script: Export Voxel Data to JSON with RGBA values
// This script exports each Y-slice of the voxel model to JSON format
// preserving full RGBA color information including alpha channel

import * as std from 'std'

goxel.registerScript({
  name: 'Export JSON Slices',
  description: 'Export voxel data to JSON with full RGBA values',
  onExecute: function() {
    // Try to access active layer material
    let activeLayer = goxel.image.activeLayer
    print('Active layer info:')
    print('  Type: ' + typeof activeLayer)
    print('  Keys: ' + Object.keys(activeLayer))

    // Try to access material if it exists
    if (activeLayer.material) {
      print('  Material found!')
      print('  Material type: ' + typeof activeLayer.material)
      print('  Material keys: ' + Object.keys(activeLayer.material))
      if (activeLayer.material.base_color) {
        print('  Base color: ' + activeLayer.material.base_color)
        print('  Alpha from material: ' + activeLayer.material.base_color[3])
      }
    } else {
      print('  No material property accessible')
    }

    // Get all layers combined into a single volume
    let volume = goxel.image.getLayersVolume()

    // First pass: collect all voxels and find bounds
    let voxels = []
    let minX = Infinity, minY = Infinity, minZ = Infinity
    let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity

    let sampleCount = 0
    let alphaValues = {}
    volume.iter(function(pos, color) {
      // Track unique alpha values
      let a = color.a || color[3] || 255
      alphaValues[a] = (alphaValues[a] || 0) + 1

      // Debug first few voxels
      if (sampleCount < 5) {
        print('Sample voxel ' + sampleCount + ':')
        print('  pos: [' + pos.x + ', ' + pos.y + ', ' + pos.z + ']')
        print('  RGBA: [' + (color.r || color[0]) + ', ' + (color.g || color[1]) + ', ' + (color.b || color[2]) + ', ' + a + ']')
        sampleCount++
      }

      voxels.push({
        x: pos.x,
        y: pos.y,
        z: pos.z,
        r: color.r || color[0],
        g: color.g || color[1],
        b: color.b || color[2],
        a: color.a || color[3]
      })

      minX = Math.min(minX, pos.x)
      minY = Math.min(minY, pos.y)
      minZ = Math.min(minZ, pos.z)
      maxX = Math.max(maxX, pos.x)
      maxY = Math.max(maxY, pos.y)
      maxZ = Math.max(maxZ, pos.z)
    })

    print('Total voxels found: ' + voxels.length)
    print('Bounds: X[' + minX + ',' + maxX + '] Y[' + minY + ',' + maxY + '] Z[' + minZ + ',' + maxZ + ']')
    print('Unique alpha values found:')
    for (let alpha in alphaValues) {
      print('  alpha=' + alpha + ': ' + alphaValues[alpha] + ' voxels')
    }

    // Calculate dimensions
    let width = maxX - minX + 1
    let height = maxY - minY + 1
    let depth = maxZ - minZ + 1

    // Structure to hold all voxel data
    let exportData = {
      width: width,
      height: height,
      depth: depth,
      minPos: [minX, minY, minZ],
      slices: []
    }

    // Group voxels by Z slice (slicing along Z axis)
    // In Goxel: X=left/right, Y=forward/back, Z=up/down
    // We want horizontal slices stacked vertically, so we slice along Z
    let sliceMap = {}
    for (let i = 0; i < voxels.length; i++) {
      let v = voxels[i]
      let sliceIndex = v.z - minZ  // Z becomes the slice index (vertical layers)

      if (!sliceMap[sliceIndex]) {
        sliceMap[sliceIndex] = []
      }

      sliceMap[sliceIndex].push({
        x: v.x - minX,  // X position within the slice
        z: v.y - minY,  // Y becomes Z in the output (rotated coordinate system)
        r: v.r,
        g: v.g,
        b: v.b,
        a: v.a
      })
    }

    // Convert sliceMap to array
    for (let sliceIndex in sliceMap) {
      exportData.slices.push({
        slice: parseInt(sliceIndex),  // Changed from 'y' to 'slice' for clarity
        pixels: sliceMap[sliceIndex]
      })
    }

    // Sort slices by index
    exportData.slices.sort(function(a, b) { return a.slice - b.slice })

    print('Slices created: ' + exportData.slices.length)
    for (let i = 0; i < exportData.slices.length; i++) {
      print('  Slice ' + exportData.slices[i].slice + ': ' + exportData.slices[i].pixels.length + ' pixels')
    }

    // Generate filename with timestamp to avoid overwriting
    let now = new Date()
    let timestamp = now.getFullYear() +
                   ('0' + (now.getMonth() + 1)).slice(-2) +
                   ('0' + now.getDate()).slice(-2) + '_' +
                   ('0' + now.getHours()).slice(-2) +
                   ('0' + now.getMinutes()).slice(-2) +
                   ('0' + now.getSeconds()).slice(-2)

    // Export to user's Documents folder for easy access
    // You can change this path to match where you save your .gox files
    let userProfile = std.getenv('USERPROFILE') || std.getenv('HOME') || '.'
//    let outputDir = userProfile + '/Documents/GoxelExports'
    let outputDir = 'C:/Users/jaymc/IdeaProjects/curly-octo-adventure/assets/templates/goxel_exports/'

    // NOTE: Make sure this directory exists before running the script!
    // The script cannot create it automatically.

    let filename = outputDir + 'voxel_export_' + timestamp + '.json'

    // Write JSON to file
    let file = std.open(filename, 'w')
    if (file) {
      file.puts(JSON.stringify(exportData, null, 2))
      file.close()
      print('Export complete: ' + filename)
      print('Exported ' + exportData.slices.length + ' slices')
    } else {
      print('ERROR: Could not write to file: ' + filename)
    }
  }
})
