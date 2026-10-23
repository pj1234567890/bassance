document.getElementById("loginButton").addEventListener("click", async () => {
    const error = document.getElementById("err");
    error.textContent = "";

    try {
        const csrfResponse = await fetch("/auth/csrf");
        if (!csrfResponse.ok) throw new Error("Could not initialize secure login");
        const csrf = await csrfResponse.json();
        const response = await fetch("/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.token },
            body: JSON.stringify({
                email: document.getElementById("email").value,
                password: document.getElementById("password").value
            })
        });

        if (!response.ok) {
            error.textContent = response.status === 429 ? "Too many attempts. Try again later." : "Invalid credentials";
            return;
        }
        window.location.href = "dashboard.html";
    } catch {
        error.textContent = "Login is temporarily unavailable. Please try again.";
    }
});
