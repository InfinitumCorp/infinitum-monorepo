FROM node:22-alpine
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages ./packages
COPY apps/conductor ./apps/conductor
RUN pnpm install --filter @infinitum/conductor... --frozen-lockfile || pnpm install --filter @infinitum/conductor...
WORKDIR /app/apps/conductor
ENV PORT=3001
EXPOSE 3001
CMD ["node", "src/server.ts"]
