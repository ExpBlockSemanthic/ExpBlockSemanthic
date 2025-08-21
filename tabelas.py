import os
import re
import pandas as pd

def montar_planilha_excel(base_path, output_file):
    # Regex para capturar os valores com base no seu exemplo real
    regex = {
        "tempo": r"Tempo de execução:\s*([\d.]+)\s*segundos",
        "pares_corresp": r"Total de pares correspondentes:\s*(\d+)",
        "falsos_pos": r"Total de falsos positivos:\s*(\d+)",
        "pares_ident": r"Total de pares identificados:\s*(\d+)",
        "precisao": r"Precisão\s*=\s*([\d.]+)",
        "recall": r"Abrangência\s*\(Recall\)\s*=\s*([\d.]+)"
    }

    with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
        alguma_aba = False

        for pasta in sorted(os.listdir(base_path)):
            if not pasta.startswith("config_"):
                continue

            pasta_path = os.path.join(base_path, pasta)
            if not os.path.isdir(pasta_path):
                continue

            dados = []
            for arquivo in sorted(os.listdir(pasta_path)):
                if not arquivo.endswith(".txt"):
                    continue

                arquivo_path = os.path.join(pasta_path, arquivo)
                with open(arquivo_path, "r", encoding="utf-8") as f:
                    conteudo = f.read()

                valores = {}
                for chave, pattern in regex.items():
                    match = re.search(pattern, conteudo)
                    valores[chave] = match.group(1) if match else None

                exec_match = re.search(r"exec_(\d+)", arquivo)
                exec_num = exec_match.group(1) if exec_match else "?"

                # Se algum dos campos for None, sinaliza para debug
                if None in valores.values():
                    print(f"[AVISO] Campo não encontrado em {arquivo_path}")
                    print("Valores encontrados:", valores)

                dados.append({
                    "Exec": exec_num,
                    "Recall": valores["recall"],
                    "Precisão": valores["precisao"],
                    "Pares Correspondentes": valores["pares_corresp"],
                    "Falsos Positivos": valores["falsos_pos"],
                    "Pares Identificados": valores["pares_ident"],
                    "Tempo de exec": valores["tempo"]
                })

            if dados:
                df = pd.DataFrame(dados)
                df = df.sort_values(by="Exec").reset_index(drop=True)

                # Trocar ponto por vírgula
                df = df.applymap(lambda x: str(x).replace(".", ",") if isinstance(x, str) else x)

                sheet_name = pasta[:31]
                df.to_excel(writer, sheet_name=sheet_name, index=False)
                alguma_aba = True

        if not alguma_aba:
            pd.DataFrame([{"Aviso": "Nenhum dado encontrado"}]).to_excel(writer, sheet_name="Vazio", index=False)

    print(f"Planilha gerada em: {output_file}")

# Exemplo de uso
base_path = r"ProjetoBD/ExpBlockSemanthic/config_1"  # caminho da pasta onde estão as config_X
output_file = os.path.join(base_path, "resultados_tabelas.xlsx")

montar_planilha_excel(base_path, output_file)
