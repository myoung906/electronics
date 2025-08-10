#!/usr/bin/env node
import fetch from 'node-fetch';
import { parseStringPromise } from 'xml2js';
import { createReadStream } from 'fs';
import { createInterface } from 'readline';

class DBpiaSearchMCP {
  constructor() {
    this.apiKey = process.env.DBPIA_API_KEY;
    if (!this.apiKey) {
      throw new Error('DBPIA_API_KEY environment variable is required');
    }
  }

  async initialize() {
    const response = {
      jsonrpc: "2.0",
      id: null,
      result: {
        protocolVersion: "2024-11-05",
        capabilities: {
          tools: {},
        },
        serverInfo: {
          name: "dbpia-search",
          version: "1.0.0",
        },
      },
    };
    return response;
  }

  async listTools() {
    return {
      jsonrpc: "2.0",
      id: null,
      result: {
        tools: [
          {
            name: "search_dbpia",
            description: "DBpia 학술 데이터베이스에서 논문을 검색합니다",
            inputSchema: {
              type: "object",
              properties: {
                query: {
                  type: "string",
                  description: "검색할 키워드"
                },
                limit: {
                  type: "number",
                  description: "검색 결과 수 제한 (기본값: 10)",
                  default: 10
                }
              },
              required: ["query"]
            }
          }
        ]
      }
    };
  }

  async callTool(name, arguments_) {
    if (name === "search_dbpia") {
      return await this.searchDBpia(arguments_.query, arguments_.limit || 10);
    }
    throw new Error(`Unknown tool: ${name}`);
  }

  async searchDBpia(query, limit = 10) {
    try {
      const encodedQuery = encodeURIComponent(query);
      const url = `http://api.dbpia.co.kr/v2/search/search.xml?key=${this.apiKey}&searchall=${encodedQuery}&countall=${limit}`;
      
      const response = await fetch(url);
      const xml = await response.text();
      const js = await parseStringPromise(xml);
      
      const docs = js.response?.documents?.[0]?.document || [];
      
      const items = docs.map(d => ({
        title: d.titl ? d.titl[0] : '',
        authors: d.auth ? d.auth[0] : '',
        journal: d.pbls ? d.pbls[0] : '',
        url: d.tid ? `http://www.dbpia.co.kr/Search/JournalDetail?zid=${d.tid[0]}` : '',
        abstract: d.abst ? d.abst[0] : '',
        year: d.year ? d.year[0] : '',
        publisher: d.publ ? d.publ[0] : ''
      }));

      return {
        jsonrpc: "2.0",
        id: null,
        result: {
          content: [
            {
              type: "text",
              text: `DBpia 검색 결과 (키워드: "${query}"):\n\n${items.map((item, index) => 
                `${index + 1}. **${item.title}**\n` +
                `   저자: ${item.authors}\n` +
                `   학술지: ${item.journal}\n` +
                `   출판년도: ${item.year}\n` +
                `   출판사: ${item.publisher}\n` +
                `   URL: ${item.url}\n` +
                `   초록: ${item.abstract.substring(0, 200)}${item.abstract.length > 200 ? '...' : ''}\n`
              ).join('\n')}`
            }
          ]
        }
      };
    } catch (error) {
      return {
        jsonrpc: "2.0",
        id: null,
        error: {
          code: -32603,
          message: `DBpia 검색 중 오류 발생: ${error.message}`
        }
      };
    }
  }

  async handleRequest(request) {
    try {
      const { method, params, id } = request;
      
      let result;
      switch (method) {
        case "initialize":
          result = await this.initialize();
          break;
        case "tools/list":
          result = await this.listTools();
          break;
        case "tools/call":
          result = await this.callTool(params.name, params.arguments);
          break;
        default:
          throw new Error(`Unknown method: ${method}`);
      }
      
      result.id = id;
      return result;
    } catch (error) {
      return {
        jsonrpc: "2.0",
        id: request.id,
        error: {
          code: -32603,
          message: error.message
        }
      };
    }
  }
}

async function main() {
  const server = new DBpiaSearchMCP();
  
  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
  });

  for await (const line of rl) {
    if (line.trim()) {
      try {
        const request = JSON.parse(line);
        const response = await server.handleRequest(request);
        console.log(JSON.stringify(response));
      } catch (error) {
        const errorResponse = {
          jsonrpc: "2.0",
          id: null,
          error: {
            code: -32700,
            message: "Parse error"
          }
        };
        console.log(JSON.stringify(errorResponse));
      }
    }
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch(console.error);
}
