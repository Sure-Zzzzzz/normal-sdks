(function () {
    const originalFetch = window.fetch;

    window.fetch = function () {
        return originalFetch.apply(this, arguments).then(function (response) {
            if (response.status === 401) {
                window.location.assign('/admin/login');
            }
            return response;
        });
    };

    window.adminRequireSuccess = function (response) {
        if (!response.ok) {
            throw new Error('操作未完成');
        }
        return response;
    };

    window.adminRequest = function (url, options) {
        const request = options || {};
        request.headers = request.headers || {};
        const csrfToken = document.querySelector('meta[name="admin-csrf-token"]');
        const csrfHeader = document.querySelector('meta[name="admin-csrf-header"]');
        if (csrfToken && csrfHeader && csrfToken.content && csrfHeader.content) {
            request.headers[csrfHeader.content] = csrfToken.content;
        }
        return window.fetch(url, request).then(window.adminRequireSuccess);
    };
}());
