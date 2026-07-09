document.addEventListener('DOMContentLoaded', () => {
    const fileList = document.getElementById('fileList');
    const breadcrumbs = document.getElementById('breadcrumbs');
    const searchInput = document.getElementById('searchInput');
    const refreshBtn = document.getElementById('refreshBtn');

    let currentPath = '/';

    // Load files for current path
    function loadFiles(path) {
        fetch(`/api/list?path=${encodeURIComponent(path)}`)
            .then(response => response.json())
            .then(files => {
                renderFiles(files);
                updateBreadcrumbs(path);
                currentPath = path;
            });
    }

    // Render file list
    function renderFiles(files) {
        fileList.innerHTML = '';

        // Add parent directory link if not root
        if (currentPath !== '/') {
            const parentPath = currentPath.split('/').slice(0, -1).join('/') || '/';
            fileList.appendChild(createFileCard({
                name: '..',
                path: parentPath,
                isDirectory: true,
                size: 0
            }));
        }

        files.forEach(file => {
            fileList.appendChild(createFileCard(file));
        });
    }

    // Create file card element
    function createFileCard(file) {
        const card = document.createElement('a');
        card.href = file.isDirectory ? `?path=${encodeURIComponent(file.path)}` : `/download?path=${encodeURIComponent(file.path)}`;
        card.className = 'file-card';

        const icon = document.createElement('div');
        icon.className = 'file-icon';
        icon.innerHTML = getFileIcon(file);

        const name = document.createElement('h3');
        name.textContent = file.name;

        const info = document.createElement('div');
        info.className = 'file-info';
        info.innerHTML = `
            <span>${file.isDirectory ? 'Folder' : formatFileSize(file.size)}</span>
            <span>${new Date(file.modified).toLocaleString()}</span>
        `;

        card.appendChild(icon);
        card.appendChild(name);
        card.appendChild(info);
        return card;
    }

    // Update breadcrumb navigation
    function updateBreadcrumbs(path) {
        breadcrumbs.innerHTML = '';

        const parts = path.split('/').filter(p => p);
        let currentPath = '';

        breadcrumbs.appendChild(createBreadcrumb('/', 'Home'));

        parts.forEach(part => {
            currentPath += `${part}/`;
            breadcrumbs.appendChild(document.createTextNode(' › '));
            breadcrumbs.appendChild(createBreadcrumb(currentPath, part));
        });
    }

    function createBreadcrumb(path, name) {
        const link = document.createElement('a');
        link.href = `?path=${encodeURIComponent(path)}`;
        link.textContent = name;
        return link;
    }

    // Get appropriate icon for file type
    function getFileIcon(file) {
        if (file.isDirectory) return '📁';

        const ext = file.name.split('.').pop().toLowerCase();
        const icons = {
            pdf: '📄',
            jpg: '🖼️',
            png: '🖼️',
            mp3: '🎵',
            mp4: '🎬',
            zip: '🗜️',
            exe: '⚙️',
            default: '📄'
        };

        return icons[ext] || icons.default;
    }

    // Format file size
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // Event listeners
    refreshBtn.addEventListener('click', () => loadFiles(currentPath));
    searchInput.addEventListener('input', (e) => {
        const searchTerm = e.target.value.toLowerCase();
        Array.from(fileList.children).forEach(card => {
            const name = card.querySelector('h3').textContent.toLowerCase();
            card.style.display = name.includes(searchTerm) ? 'block' : 'none';
        });
    });

    // Initialize
    const urlParams = new URLSearchParams(window.location.search);
    const initialPath = urlParams.get('path') || '/';
    loadFiles(initialPath);
});