// Navigation functions
function goToParent() {
    const pathElement = document.querySelector('.path');
    if (!pathElement) return;
    const currentPath = pathElement.textContent.trim();
    const parentPath = currentPath.split(/[/\\]/).slice(0, -1).join('/') || '/';
    if (parentPath && parentPath !== currentPath) {
        window.location.href = '/browse?path=' + encodeURIComponent(parentPath);
    }
}

function goToRoot() {
    window.location.href = '/browse';
}

// Folder creation
function createFolder() {
    const pathElement = document.querySelector('.path');
    if (!pathElement) return;
    const currentPath = pathElement.textContent.trim();
    const folderName = prompt('Enter folder name:');
    if (!folderName || !folderName.trim()) return;
    
    const formData = new URLSearchParams();
    formData.append('path', currentPath);
    formData.append('name', folderName.trim());
    
    fetch('/mkdir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    }).then(response => response.json())
      .then(data => {
          if (data.status === 'success') {
              location.reload();
          } else {
              alert('Error: ' + data.message);
          }
      }).catch(error => {
          console.error('Error creating folder:', error);
          alert('Failed to create folder');
      });
}

// Toggle functions
function toggleUpload() {
    const uploadZone = document.getElementById('uploadZone');
    if (!uploadZone) return;
    
    if (uploadZone.style.display === 'none' || uploadZone.style.display === '') {
        uploadZone.style.display = 'block';
    } else {
        uploadZone.style.display = 'none';
    }
}

function toggleSearch() {
    const searchBox = document.getElementById('searchBox');
    if (searchBox) {
        searchBox.style.display = searchBox.style.display === 'none' ? 'block' : 'none';
    }
}

function toggleThemeMenu() {
    const themeMenu = document.getElementById('themeMenu');
    if (themeMenu) {
        themeMenu.style.display = themeMenu.style.display === 'none' ? 'block' : 'none';
    }
}

// Sorting functions
function sortByName() {
    const currentPath = document.querySelector('.path')?.textContent.trim() || '';
    const url = new URL(window.location);
    url.searchParams.set('sortBy', 'name');
    if (currentPath) {
        url.searchParams.set('path', currentPath);
    }
    window.location.href = url.toString();
}

function sortByType() {
    const currentPath = document.querySelector('.path')?.textContent.trim() || '';
    const url = new URL(window.location);
    url.searchParams.set('sortBy', 'type');
    if (currentPath) {
        url.searchParams.set('path', currentPath);
    }
    window.location.href = url.toString();
}

// Search functions
function performSearch() {
    const searchInput = document.getElementById('searchInput');
    const searchContent = document.getElementById('searchContent');
    const pathElement = document.querySelector('.path');

    if (!searchInput) return;

    const query = searchInput.value.trim();
    if (!query) {
        alert('Please enter a search term');
        return;
    }

    const currentPath = pathElement?.textContent.trim() || '';
    const contentSearch = searchContent?.checked || false;

    const url = '/search?q=' + encodeURIComponent(query) +
                '&path=' + encodeURIComponent(currentPath) +
                '&content=' + (contentSearch ? 'true' : 'false');

    // Show loading state in the file table
    const tbody = document.querySelector('.file-table tbody');
    if (tbody) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty">🔍 Searching for "<strong>' + escapeHtml(query) + '</strong>"...</td></tr>';
    }

    fetch(url)
        .then(r => r.json())
        .then(data => {
            if (!tbody) return;
            if (!data.results || data.results.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" class="empty">No results found for "<strong>' + escapeHtml(query) + '</strong>"</td></tr>';
                return;
            }

            const iconPack = localStorage.getItem('iconPack') || 'emoji';
            const rows = data.results.map(result => {
                const icon = result.isDirectory ? '📁' : getIconForFile(result.name, false, iconPack);
                const encodedPath = encodeURIComponent(result.path);
                const ext = result.name.split('.').pop().toLowerCase();
                const isImage = ['jpg','jpeg','png','gif','bmp','webp','svg'].includes(ext);
                const isVideo = ['mp4','mov','avi','mkv','webm'].includes(ext);
                const isAudio = ['mp3','wav','ogg','aac','flac','m4a'].includes(ext);
                const isPdf = ext === 'pdf';

                let link;
                if (result.isDirectory) {
                    link = '/browse?path=' + encodedPath;
                } else if (isImage) {
                    const dirPath = encodeURIComponent(result.path.substring(0, result.path.lastIndexOf('/')));
                    link = '/gallery?path=' + dirPath + '&file=' + encodedPath;
                } else if (isVideo || isAudio) {
                    link = '/player?file=' + encodedPath;
                } else if (isPdf) {
                    link = '/pdf-viewer?file=' + encodedPath;
                } else {
                    link = '/download?file=' + encodedPath;
                }

                const snippet = result.snippet
                    ? '<br><small style="color:var(--text-secondary);font-size:11px;">Line ' + (result.lineNumber || '') + ': ' + escapeHtml(result.snippet) + '</small>'
                    : '';
                const matchBadge = result.matchType === 'FILE_CONTENT'
                    ? '<span style="font-size:10px;background:var(--primary-color);color:white;padding:1px 5px;border-radius:4px;margin-left:6px;">content</span>'
                    : '';

                return '<tr>' +
                    '<td class="col-name"><a class="filename" href="' + link + '"><span class="icon">' + icon + '</span>' + escapeHtml(result.name) + matchBadge + '</a>' + snippet + '</td>' +
                    '<td class="col-modified"></td>' +
                    '<td class="col-size"></td>' +
                    '<td class="col-actions"><button class="delete-btn" onclick="deleteItem(\'' + encodedPath + '\', \'' + escapeHtml(result.name).replace(/'/g, "\\'") + '\')">🗑️</button></td>' +
                    '</tr>';
            }).join('');

            tbody.innerHTML = rows;

            // Re-apply icon pack to new rows
            const savedPack = localStorage.getItem('iconPack');
            if (savedPack && savedPack !== 'emoji') applyIconPack(savedPack);
        })
        .catch(err => {
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="4" class="empty error">Search failed: ' + err.message + '</td></tr>';
            }
        });
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function clearSearch() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) searchInput.value = '';
    toggleSearch();
    // Reload the page to restore the original file listing
    location.reload();
}

// Theme functions
function setTheme(theme) {
    document.body.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
    // Only clear active from color theme buttons (not icon pack buttons)
    document.querySelectorAll('.theme-btn[data-type="theme"]').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.querySelector(`.theme-btn[data-type="theme"][onclick="setTheme('${theme}')"]`);
    if (activeBtn) activeBtn.classList.add('active');
    toggleThemeMenu();
}

function setIconPack(pack) {
    localStorage.setItem('iconPack', pack);
    applyIconPack(pack);
    // Only clear active from icon pack buttons
    document.querySelectorAll('.theme-btn[data-type="icons"]').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.querySelector(`.theme-btn[data-type="icons"][onclick="setIconPack('${pack}')"]`);
    if (activeBtn) activeBtn.classList.add('active');
    toggleThemeMenu();
}

// Icon definitions for each pack
const iconPacks = {
    emoji: {
        folder: '📁', image: '🖼️', audio: '🎵', video: '🎬', pdf: '📄',
        word: '📄', excel: '📊', ppt: '🖥️', text: '📝', archive: '📦',
        apk: '📱', web: '💻', unknown: '❓'
    },
    minimal: {
        folder: '▶', image: '▣', audio: '♪', video: '▶', pdf: '≡',
        word: '≡', excel: '⊞', ppt: '▤', text: '≡', archive: '⊟',
        apk: '⊕', web: '⊙', unknown: '·'
    },
    colorful: {
        folder: '🗂️', image: '🌅', audio: '🎶', video: '📽️', pdf: '📑',
        word: '📘', excel: '📗', ppt: '📙', text: '📃', archive: '🗜️',
        apk: '📲', web: '🌐', unknown: '📎'
    }
};

function getIconForFile(filename, isDirectory, pack) {
    const icons = iconPacks[pack] || iconPacks.emoji;
    if (isDirectory) return icons.folder;
    const ext = filename.split('.').pop().toLowerCase();
    if (['jpg','jpeg','png','gif','bmp','webp','svg'].includes(ext)) return icons.image;
    if (['mp3','wav','ogg','aac','flac'].includes(ext)) return icons.audio;
    if (['mp4','mov','avi','mkv','webm'].includes(ext)) return icons.video;
    if (ext === 'pdf') return icons.pdf;
    if (['doc','docx'].includes(ext)) return icons.word;
    if (['xls','xlsx'].includes(ext)) return icons.excel;
    if (['ppt','pptx'].includes(ext)) return icons.ppt;
    if (['txt','md','log'].includes(ext)) return icons.text;
    if (['zip','rar','7z','tar','gz'].includes(ext)) return icons.archive;
    if (ext === 'apk') return icons.apk;
    if (['html','htm','css','js'].includes(ext)) return icons.web;
    return icons.unknown;
}

function applyIconPack(pack) {
    document.querySelectorAll('.file-table tbody tr').forEach(row => {
        const iconSpan = row.querySelector('.icon');
        const link = row.querySelector('a.filename');
        if (!iconSpan || !link) return;

        // Store original server-rendered icon the first time
        if (!iconSpan.dataset.original) {
            iconSpan.dataset.original = iconSpan.textContent;
        }

        if (pack === 'emoji') {
            // Restore original server-rendered emoji
            iconSpan.textContent = iconSpan.dataset.original;
            return;
        }

        const href = link.getAttribute('href') || '';
        const isDir = href.startsWith('/browse');
        const filename = link.textContent.replace(iconSpan.textContent, '').trim();
        iconSpan.textContent = getIconForFile(filename, isDir, pack);
    });
}

// Delete function
function deleteItem(path, name) {
    if (!confirm('Are you sure you want to delete "' + name + '"?')) {
        return;
    }
    
    const formData = new URLSearchParams();
    formData.append('path', path);
    
    fetch('/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    }).then(response => response.json())
      .then(data => {
          if (data.status === 'success') {
              location.reload();
          } else {
              alert('Error: ' + data.message);
          }
      }).catch(error => {
          console.error('Error deleting item:', error);
          alert('Failed to delete item');
      });
}

// Archive extraction
function extractArchive(path, name) {
    if (!confirm('Extract "' + name + '"?')) {
        return;
    }
    
    const formData = new URLSearchParams();
    formData.append('file', path);
    
    fetch('/archive/extract', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    }).then(response => response.json())
      .then(data => {
          if (data.status === 'success') {
              alert('Archive extracted successfully');
              location.reload();
          } else {
              alert('Error: ' + data.message);
          }
      }).catch(error => {
          console.error('Error extracting archive:', error);
          alert('Failed to extract archive');
      });
}

// PDF Conversion function for browse mode
function convertPdfToImages(filePath, fileName) {
    // Create dialog if it doesn't exist
    var existingDialog = document.getElementById('pdfConvertDialog');
    if (existingDialog) {
        existingDialog.remove();
    }
    
    var dialog = document.createElement('div');
    dialog.id = 'pdfConvertDialog';
    dialog.style.cssText = 'position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,0.9); z-index:2000; padding:20px;';
    
    var dialogContent = document.createElement('div');
    dialogContent.style.cssText = 'max-width:500px; margin:50px auto; background:#2d2d2d; padding:30px; border-radius:10px;';
    
    dialogContent.innerHTML = `
        <h2 style="margin-top:0; color:#fff;">Convert PDF to Images</h2>
        <div style="margin:20px 0;">
            <label style="display:block; margin-bottom:5px; color:#ccc;">Format:</label>
            <select id="pdfFormat" style="width:100%; padding:8px; background:#3a3a3a; color:#fff; border:1px solid #555; border-radius:4px;">
                <option value="png">PNG (Best Quality)</option>
                <option value="jpg">JPEG (Smaller Size)</option>
            </select>
        </div>
        <div style="margin:20px 0;">
            <label style="display:block; margin-bottom:5px; color:#ccc;">Resolution:</label>
            <select id="pdfScale" style="width:100%; padding:8px; background:#3a3a3a; color:#fff; border:1px solid #555; border-radius:4px;">
                <option value="0.5">Low (36 DPI)</option>
                <option value="1.0">Standard (72 DPI)</option>
                <option value="1.5">Medium (108 DPI)</option>
                <option value="2.0">High (144 DPI)</option>
                <option value="2.5">Very High (180 DPI)</option>
                <option value="3.0" selected>Ultra (216 DPI)</option>
                <option value="4.0">Super (288 DPI)</option>
                <option value="5.0">Maximum (360 DPI)</option>
            </select>
        </div>
        <div style="margin-top:30px; display:flex; gap:10px;">
            <button id="pdfConvertBtn" style="flex:1; padding:12px; background:#4CAF50; color:white; border:none; border-radius:6px; cursor:pointer; font-size:16px;">Convert</button>
            <button id="pdfCancelBtn" style="flex:1; padding:12px; background:#666; color:white; border:none; border-radius:6px; cursor:pointer; font-size:16px;">Cancel</button>
        </div>
        <div id="pdfStatus" style="margin-top:20px; padding:10px; background:#3a3a3a; border-radius:4px; display:none; color:#fff;"></div>
    `;
    
    dialog.appendChild(dialogContent);
    document.body.appendChild(dialog);
    
    var statusDiv = document.getElementById('pdfStatus');
    var cancelBtn = document.getElementById('pdfCancelBtn');
    var convertBtn = document.getElementById('pdfConvertBtn');
    
    cancelBtn.onclick = function() {
        dialog.remove();
    };
    
    convertBtn.onclick = function() {
        var format = document.getElementById('pdfFormat').value;
        var scale = document.getElementById('pdfScale').value;
        
        statusDiv.style.display = 'block';
        statusDiv.innerHTML = '<div style="color:#4CAF50;">Converting...</div>';
        
        var params = 'file=' + encodeURIComponent(filePath) + '&format=' + format + '&scale=' + scale;
        
        fetch('/pdf-convert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        }).then(function(response) {
            if (!response.ok) {
                throw new Error('HTTP error! status: ' + response.status);
            }
            return response.json();
        }).then(function(data) {
            if (data.status === 'success') {
                statusDiv.innerHTML = '<div style="color:#4CAF50;">' + data.message + '<br>Converted ' + data.convertedPages + ' pages<br><button onclick="location.href=\'/browse?path=' + encodeURIComponent(data.outputDir) + '\'" style="margin-top:10px;padding:8px 16px;background:#2196F3;color:white;border:none;border-radius:4px;cursor:pointer;">View Images</button></div>';
            } else {
                statusDiv.innerHTML = '<div style="color:#ff6b6b;">Error: ' + data.message + '</div>';
            }
        }).catch(function(error) {
            console.error('PDF conversion error:', error);
            statusDiv.innerHTML = '<div style="color:#ff6b6b;">Failed: ' + error.message + '</div>';
        });
    };
}

// Multi-select functions
function getSelectedRows() {
    return document.querySelectorAll('.file-table tbody tr.selected');
}

function getSelectedPaths() {
    const selectedRows = getSelectedRows();
    const paths = [];
    selectedRows.forEach(row => {
        const checkbox = row.querySelector('input[type="checkbox"][data-path]');
        if (checkbox) {
            paths.push(checkbox.getAttribute('data-path'));
        }
    });
    return paths;
}

function toggleRowSelection(row) {
    if (!row) return;
    row.classList.toggle('selected');
    const checkbox = row.querySelector('input[type="checkbox"][data-path]');
    if (checkbox) {
        checkbox.checked = row.classList.contains('selected');
    }
    updateSelectionUI();
}

function updateSelectionUI() {
    const selectedCount = getSelectedRows().length;
    const countElement = document.getElementById('selectedCount');
    const multiSelectActions = document.getElementById('multiSelectActions');
    
    if (countElement) {
        countElement.textContent = selectedCount;
    }
    
    if (multiSelectActions) {
        if (selectedCount > 0) {
            multiSelectActions.classList.add('show');
        } else {
            multiSelectActions.classList.remove('show');
        }
    }
}

function downloadSelected() {
    const paths = getSelectedPaths();
    if (paths.length === 0) {
        alert('No files selected');
        return;
    }
    
    // Download each selected file
    paths.forEach(path => {
        window.open('/download?file=' + encodeURIComponent(path), '_blank');
    });
}

function createArchiveFromSelected() {
    const paths = getSelectedPaths();
    if (paths.length === 0) {
        alert('No files selected');
        return;
    }
    
    const pathElement = document.querySelector('.path');
    const currentPath = pathElement?.textContent.trim() || '';
    const archiveName = prompt('Enter archive name (without extension):', 'archive');
    if (!archiveName || !archiveName.trim()) return;
    
    const formData = new URLSearchParams();
    formData.append('files', paths.join(','));
    formData.append('output', currentPath + '/' + archiveName.trim() + '.zip');
    
    fetch('/archive/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    }).then(response => response.json())
      .then(data => {
          if (data.status === 'success') {
              alert('Archive created successfully');
              clearSelection();
              location.reload();
          } else {
              alert('Error: ' + data.message);
          }
      }).catch(error => {
          console.error('Error creating archive:', error);
          alert('Failed to create archive');
      });
}

function deleteSelected() {
    const paths = getSelectedPaths();
    if (paths.length === 0) {
        alert('No files selected');
        return;
    }
    
    if (!confirm('Are you sure you want to delete ' + paths.length + ' item(s)?')) {
        return;
    }
    
    let deletedCount = 0;
    let errorCount = 0;
    
    paths.forEach(path => {
        const formData = new URLSearchParams();
        formData.append('path', path);
        
        fetch('/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData
        }).then(response => response.json())
          .then(data => {
              if (data.status === 'success') {
                  deletedCount++;
              } else {
                  errorCount++;
              }
              
              if (deletedCount + errorCount === paths.length) {
                  if (errorCount === 0) {
                      location.reload();
                  } else {
                      alert('Deleted ' + deletedCount + ' item(s), ' + errorCount + ' error(s)');
                      location.reload();
                  }
              }
          }).catch(error => {
              errorCount++;
              if (deletedCount + errorCount === paths.length) {
                  alert('Deleted ' + deletedCount + ' item(s), ' + errorCount + ' error(s)');
                  location.reload();
              }
          });
    });
}

function clearSelection() {
    const selectedRows = getSelectedRows();
    selectedRows.forEach(row => {
        row.classList.remove('selected');
        const checkbox = row.querySelector('input[type="checkbox"][data-path]');
        if (checkbox) {
            checkbox.checked = false;
        }
    });
    updateSelectionUI();
}

function selectAll() {
    const rows = document.querySelectorAll('.file-table tbody tr');
    rows.forEach(row => {
        if (!row.classList.contains('selected')) {
            row.classList.add('selected');
            const checkbox = row.querySelector('input[type="checkbox"][data-path]');
            if (checkbox) {
                checkbox.checked = true;
            }
        }
    });
    updateSelectionUI();
}

function moveSelected() {
    const paths = getSelectedPaths();
    if (paths.length === 0) {
        alert('No files selected');
        return;
    }
    
    showDirectoryPicker('move', paths);
}

function copySelected() {
    const paths = getSelectedPaths();
    if (paths.length === 0) {
        alert('No files selected');
        return;
    }
    
    showDirectoryPicker('copy', paths);
}

function showDirectoryPicker(operation, filePaths) {
    const pathElement = document.querySelector('.path');
    const currentPath = pathElement?.textContent.trim() || '';
    
    // Create modal dialog
    const dialog = document.createElement('div');
    dialog.id = 'directoryPickerDialog';
    dialog.style.cssText = 'position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,0.8); z-index:2000; display:flex; align-items:center; justify-content:center; padding:20px;';
    
    const dialogContent = document.createElement('div');
    dialogContent.style.cssText = 'background:#fff; border-radius:8px; width:100%; max-width:600px; max-height:80vh; display:flex; flex-direction:column;';
    
    dialogContent.innerHTML = `
        <div style="padding:20px; border-bottom:1px solid #dee2e6;">
            <h2 style="margin:0; font-size:20px;">${operation === 'move' ? 'Move' : 'Copy'} ${filePaths.length} item(s) to...</h2>
        </div>
        <div style="padding:15px; background:#f8f9fa; border-bottom:1px solid #dee2e6;">
            <div style="font-size:12px; color:#6c757d; margin-bottom:5px;">Current Location:</div>
            <div id="currentDirPath" style="font-family:monospace; font-size:14px; word-break:break-all;">${currentPath}</div>
        </div>
        <div id="directoryList" style="flex:1; overflow-y:auto; padding:10px;"></div>
        <div style="padding:15px; border-top:1px solid #dee2e6; display:flex; gap:10px; justify-content:flex-end;">
            <button id="dirPickerCancel" style="padding:10px 20px; background:#6c757d; color:white; border:none; border-radius:4px; cursor:pointer;">Cancel</button>
            <button id="dirPickerSelect" style="padding:10px 20px; background:#28a745; color:white; border:none; border-radius:4px; cursor:pointer;">${operation === 'move' ? 'Move Here' : 'Copy Here'}</button>
        </div>
    `;
    
    dialog.appendChild(dialogContent);
    document.body.appendChild(dialog);
    
    let selectedDestination = currentPath;
    
    // Load directories
    loadDirectories(currentPath);
    
    function loadDirectories(path) {
        fetch('/browse?path=' + encodeURIComponent(path))
            .then(response => response.text())
            .then(html => {
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');
                const directoryList = document.getElementById('directoryList');
                const currentDirPath = document.getElementById('currentDirPath');
                
                if (!directoryList || !currentDirPath) return;
                
                selectedDestination = path;
                currentDirPath.textContent = path;
                directoryList.innerHTML = '';
                
                // Add parent directory option if not at root
                if (path && path !== '/' && path.split(/[/\\]/).filter(p => p).length > 0) {
                    const parentBtn = document.createElement('div');
                    parentBtn.style.cssText = 'padding:12px; margin:5px 0; background:#e9ecef; border-radius:4px; cursor:pointer; display:flex; align-items:center; gap:10px;';
                    parentBtn.innerHTML = '<span style="font-size:20px;">⬆️</span><span style="font-weight:bold;">.. (Parent Directory)</span>';
                    parentBtn.onclick = () => {
                        const parentPath = path.split(/[/\\]/).slice(0, -1).join('/') || '/';
                        loadDirectories(parentPath);
                    };
                    directoryList.appendChild(parentBtn);
                }
                
                // Extract directories from the HTML
                const rows = doc.querySelectorAll('.file-table tbody tr');
                let dirCount = 0;
                
                rows.forEach(row => {
                    const link = row.querySelector('a.filename');
                    const icon = row.querySelector('.icon');
                    
                    if (link && icon && icon.textContent.includes('📁')) {
                        const href = link.getAttribute('href');
                        if (href && href.startsWith('/browse?path=')) {
                            const dirPath = decodeURIComponent(href.split('path=')[1]);
                            const dirName = link.textContent.trim();
                            
                            const dirBtn = document.createElement('div');
                            dirBtn.style.cssText = 'padding:12px; margin:5px 0; background:#f8f9fa; border:1px solid #dee2e6; border-radius:4px; cursor:pointer; display:flex; align-items:center; gap:10px; transition:background 0.2s;';
                            dirBtn.innerHTML = '<span style="font-size:20px;">📁</span><span>' + dirName + '</span>';
                            dirBtn.onmouseenter = () => dirBtn.style.background = '#e9ecef';
                            dirBtn.onmouseleave = () => dirBtn.style.background = '#f8f9fa';
                            dirBtn.onclick = () => loadDirectories(dirPath);
                            
                            directoryList.appendChild(dirBtn);
                            dirCount++;
                        }
                    }
                });
                
                if (dirCount === 0 && (!path || path === '/' || path.split(/[/\\]/).filter(p => p).length <= 0)) {
                    directoryList.innerHTML += '<div style="padding:20px; text-align:center; color:#6c757d;">No subdirectories found</div>';
                } else if (dirCount === 0) {
                    directoryList.innerHTML += '<div style="padding:20px; text-align:center; color:#6c757d;">No subdirectories in this folder</div>';
                }
            })
            .catch(error => {
                console.error('Error loading directories:', error);
                const directoryList = document.getElementById('directoryList');
                if (directoryList) {
                    directoryList.innerHTML = '<div style="padding:20px; text-align:center; color:#dc3545;">Error loading directories</div>';
                }
            });
    }
    
    // Cancel button
    document.getElementById('dirPickerCancel').onclick = () => {
        dialog.remove();
    };
    
    // Select button
    document.getElementById('dirPickerSelect').onclick = () => {
        dialog.remove();
        
        const formData = new URLSearchParams();
        formData.append('files', filePaths.join(','));
        formData.append('destination', selectedDestination);
        
        const endpoint = operation === 'move' ? '/move' : '/copy';
        
        fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData
        }).then(response => response.json())
          .then(data => {
              if (data.status === 'success') {
                  alert(data.message);
                  clearSelection();
                  location.reload();
              } else {
                  alert('Error: ' + data.message);
              }
          }).catch(error => {
              console.error('Error ' + operation + 'ing files:', error);
              alert('Failed to ' + operation + ' files');
          });
    };
    
    // Close on background click
    dialog.onclick = (e) => {
        if (e.target === dialog) {
            dialog.remove();
        }
    };
}

// File upload functions
function handleFiles(files) {
    if (!files || files.length === 0) return;
    
    const uploadZone = document.getElementById('uploadZone');
    const uploadAnimation = document.getElementById('uploadAnimation');
    const uploadText = document.getElementById('uploadText');
    const progressBar = document.getElementById('progressBar');
    const uploadStatus = document.getElementById('uploadStatus');
    const overwrite = document.getElementById('overwrite')?.checked || false;
    
    if (!uploadZone) return;
    
    uploadZone.style.display = 'block';
    if (uploadAnimation) uploadAnimation.style.display = 'flex';
    if (uploadText) uploadText.style.display = 'none';
    
    const fileArray = Array.from(files);
    let completed = 0;
    let failed = 0;
    
    function uploadNext(index) {
        if (index >= fileArray.length) {
            if (uploadStatus) {
                uploadStatus.textContent = `Uploaded ${completed} file(s)${failed > 0 ? ', ' + failed + ' failed' : ''}`;
            }
            setTimeout(() => location.reload(), 1000);
            return;
        }
        
        const file = fileArray[index];
        if (uploadStatus) uploadStatus.textContent = `Uploading ${index + 1} of ${fileArray.length}: ${file.name}`;
        if (progressBar) progressBar.style.width = ((index / fileArray.length) * 100) + '%';
        
        const formData = new FormData();
        // Send one file at a time with its real name as the field name
        formData.append('file', file, file.name);
        formData.append('uploadPath', currentUploadPath || '');
        formData.append('filename', file.name);
        if (overwrite) formData.append('overwrite', 'on');
        
        const xhr = new XMLHttpRequest();
        xhr.addEventListener('load', function() {
            if (xhr.status === 200) {
                completed++;
            } else {
                failed++;
            }
            uploadNext(index + 1);
        });
        xhr.addEventListener('error', function() {
            failed++;
            uploadNext(index + 1);
        });
        xhr.open('POST', '/upload');
        xhr.send(formData);
    }
    
    uploadNext(0);
}

// Drag and drop handlers
function dragOverHandler(ev) {
    ev.preventDefault();
    ev.stopPropagation();
    ev.currentTarget.style.background = 'rgba(33, 150, 243, 0.2)';
}

function dragEnterHandler(ev) {
    ev.preventDefault();
    ev.stopPropagation();
    ev.currentTarget.style.background = 'rgba(33, 150, 243, 0.3)';
}

function dragLeaveHandler(ev) {
    ev.preventDefault();
    ev.stopPropagation();
    ev.currentTarget.style.background = '';
}

function dropHandler(ev) {
    ev.preventDefault();
    ev.stopPropagation();
    ev.currentTarget.style.background = '';
    
    const files = ev.dataTransfer.files;
    if (files && files.length > 0) {
        const fileInput = document.getElementById('fileInput');
        if (fileInput) {
            // Create a new FileList-like object
            const dataTransfer = new DataTransfer();
            for (let i = 0; i < files.length; i++) {
                dataTransfer.items.add(files[i]);
            }
            fileInput.files = dataTransfer.files;
            handleFiles(fileInput.files);
        }
    }
}

// Initialize theme and row selection on load
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.body.setAttribute('data-theme', savedTheme);
    const activeThemeBtn = document.querySelector(`.theme-btn[data-type="theme"][onclick="setTheme('${savedTheme}')"]`);
    if (activeThemeBtn) activeThemeBtn.classList.add('active');

    const savedIconPack = localStorage.getItem('iconPack') || 'emoji';
    if (savedIconPack !== 'emoji') {
        applyIconPack(savedIconPack);
    }
    const activeIconBtn = document.querySelector(`.theme-btn[data-type="icons"][onclick="setIconPack('${savedIconPack}')"]`);
    if (activeIconBtn) activeIconBtn.classList.add('active');
    
    // Initialize row selection
    const tableRows = document.querySelectorAll('.file-table tbody tr');
    tableRows.forEach(row => {
        // Add checkbox if not present
        if (!row.querySelector('input[type="checkbox"][data-path]')) {
            const firstCell = row.querySelector('td.col-name');
            if (firstCell) {
                const link = firstCell.querySelector('a.filename');
                if (link) {
                    const href = link.getAttribute('href');
                    let path = '';
                    
                    // Extract path from href
                    if (href.startsWith('/browse?path=')) {
                        path = decodeURIComponent(href.split('path=')[1]);
                    } else if (href.startsWith('/download?file=')) {
                        path = decodeURIComponent(href.split('file=')[1]);
                    } else if (href.startsWith('/gallery?path=')) {
                        const params = new URLSearchParams(href.split('?')[1]);
                        path = decodeURIComponent(params.get('file') || '');
                    } else if (href.startsWith('/player?file=')) {
                        path = decodeURIComponent(href.split('file=')[1]);
                    } else if (href.startsWith('/pdf-viewer?file=')) {
                        path = decodeURIComponent(href.split('file=')[1]);
                    }
                    
                    if (path) {
                        const checkbox = document.createElement('input');
                        checkbox.type = 'checkbox';
                        checkbox.style.cssText = 'margin-right: 8px; vertical-align: middle; display: inline-block;';
                        checkbox.setAttribute('data-path', path);
                        checkbox.addEventListener('click', function(e) {
                            e.stopPropagation();
                            toggleRowSelection(row);
                        });
                        
                        // Make sure the cell and link are inline
                        firstCell.style.display = 'flex';
                        firstCell.style.alignItems = 'center';
                        link.style.display = 'flex';
                        link.style.alignItems = 'center';
                        link.style.flex = '1';
                        
                        // Insert checkbox at the start of the cell, before the link
                        firstCell.insertBefore(checkbox, link);
                    }
                }
            }
        }
        
        // Add click handler to row (but not on links or buttons)
        row.addEventListener('click', function(e) {
            // Don't toggle if clicking on a link, button, or checkbox
            if (e.target.tagName === 'A' || e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') {
                return;
            }
            
            // Check if click is on a link or button's parent
            if (e.target.closest('a') || e.target.closest('button')) {
                return;
            }
            
            toggleRowSelection(row);
        });
    });
    
    updateSelectionUI();
});
