FROM node:22-alpine
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages ./packages
COPY apps/spectre ./apps/spectre
RUN pnpm install --filter @infinitum/spectre... --frozen-lockfile || pnpm install --filter @infinitum/spectre...
WORKDIR /app/apps/spectre
ENV PORT=3000
EXPOSE 3000
CMD ["node", "src/server/main.ts"]
