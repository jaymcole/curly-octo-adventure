// Goxel Script: Export Voxel Data to JSON with RGBA values
// This script exports each Y-slice of the voxel model to JSON format
// preserving full RGBA color information including alpha channel

import * as std from 'std'

goxel.registerScript({
  name: 'Export JSON Slices',
  description: 'Export voxel data to JSON with full RGBA values',
  onExecute: function() {
    // Get all layers combined into a single volume
    let volume = goxel.image.getLayersVolume()

    // First pass: collect all voxels and find bounds
    let voxels = []
    let minX = Infinity, minY = Infinity, minZ = Infinity
    let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity

    let sampleCount = 0
    volume.iter(function(pos, color) {
      // Debug first few voxels
      if (sampleCount < 3) {
        print('Sample voxel:')
        print('  pos.x=' + pos.x + ', pos.y=' + pos.y + ', pos.z=' + pos.z)
        print('  color.r=' + color.r + ', color.g=' + color.g + ', color.b=' + color.b + ', color.a=' + color.a)
        print('  Object.keys(color)=' + Object.keys(color))
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

    // Group voxels by Y slice
    let sliceMap = {}
    for (let i = 0; i < voxels.length; i++) {
      let v = voxels[i]
      let relY = v.y - minY

      if (!sliceMap[relY]) {
        sliceMap[relY] = []
      }

      sliceMap[relY].push({
        x: v.x - minX,  // Relative X position
        z: v.z - minZ,  // Relative Z position
        r: v.r,
        g: v.g,
        b: v.b,
        a: v.a
      })
    }

    // Convert sliceMap to array
    for (let y in sliceMap) {
      exportData.slices.push({
        y: parseInt(y),
        pixels: sliceMap[y]
      })
    }

    // Sort slices by Y
    exportData.slices.sort(function(a, b) { return a.y - b.y })

    print('Slices created: ' + exportData.slices.length)
    for (let i = 0; i < exportData.slices.length; i++) {
      print('  Slice ' + exportData.slices[i].y + ': ' + exportData.slices[i].pixels.length + ' pixels')
    }

    // Generate filename with timestamp to avoid overwriting
    let now = new Date()
    let timestamp = now.getFullYear() +
                   ('0' + (now.getMonth() + 1)).slice(-2) +
                   ('0' + now.getDate()).slice(-2) + '_' +
                   ('0' + now.getHours()).slice(-2) +
                   ('0' + now.getMinutes()).slice(-2) +
                   ('0' + now.getSeconds()).slice(-2)

    // Use absolute path to user's temp directory or documents
    // For Windows, use the user's temp directory which is always writable
    let userProfile = std.getenv('USERPROFILE') || std.getenv('HOME') || '.'
    let outputDir = userProfile + '/AppData/Local/Temp'
    let filename = outputDir + '/voxel_export_' + timestamp + '.json'

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
