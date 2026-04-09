import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Configure the Vite development server to proxy API requests to Spring Boot
// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        // This is a safety check: 
        // If the request looks like a style or script, keep it in the frontend.
        bypass: (req) => {
          if (req.headers.accept?.indexOf('html') !== -1 || req.url.includes('.css') || req.url.includes('.js')) {
            return req.url;
          }
        },
        rewrite: (path) => path.replace(/^\/api\/v1/, '')
      }
    }
  }
})
