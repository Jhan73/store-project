FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1
ENV NODE_ENV=production \
    PORT=4000
WORKDIR /app
# The server bundle runs on node alone, so npm is only unpatched dependencies waiting to be flagged.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx
COPY --from=build /app/dist/frontend ./dist/frontend
USER node
EXPOSE 4000
CMD ["node", "dist/frontend/server/server.mjs"]
