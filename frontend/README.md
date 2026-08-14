# Frontend — Maitri

This directory contains the frontend for the Maitri application.

## Technology
- HTML5
- Vanilla CSS (no Bootstrap, no Tailwind)
- Vanilla JavaScript ES6+ (no React, Angular, Vue)

## Structure (to be created in Phase 2)
```
frontend/
├── public/
│   ├── pages/       ← HTML pages
│   ├── css/         ← Stylesheets
│   ├── js/          ← JavaScript files
│   └── assets/      ← Images, icons
└── index.html       ← Entry point / Landing page
```

## Running the Frontend

1. Open this `frontend/` folder in VS Code
2. Install the **Live Server** extension if not already installed
3. Right-click `index.html` → **Open with Live Server**
4. Frontend runs at: `http://localhost:5500`

The frontend communicates with the backend at `http://localhost:8080/api`.
Make sure the backend (Spring Boot) is running before testing API calls.

## Phase 2 Note

Frontend HTML/CSS/JS files will be created in Phase 2.
This README is a placeholder to establish the directory.
