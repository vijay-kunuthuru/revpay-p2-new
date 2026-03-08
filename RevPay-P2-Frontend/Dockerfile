# Frontend Dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build --prod

FROM nginx:alpine

# add custom nginx config to proxy /api to backend service
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/revpay-frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]