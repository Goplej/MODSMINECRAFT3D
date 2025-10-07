import time

def get_ai_response(prompt: str) -> str:
    """
    Simulates a call to a powerful AI model.

    In a real application, this function would make an API call to a service
    like OpenAI, Google AI, or a local model. For this example, it returns
    a pre-written, helpful code snippet regardless of the input.
    """
    print("AI ассистент думает...")
    time.sleep(1.5) # Имитируем задержку ответа от нейросети

    # Это заранее заготовленный ответ, который будет возвращаться на любой запрос.
    # В будущем здесь будет реальный вызов API.
    mock_response = """
    Вот пример функции на Python для сложения двух чисел, как вы просили:

    ```python
    def add(a, b):
      \"\"\"Эта функция складывает два числа и возвращает результат.\"\"\"
      return a + b

    # Пример использования:
    result = add(5, 3)
    print(f"Результат сложения: {result}")
    ```
    """
    return mock_response

def main():
    """
    Main function to run the AI assistant chat loop.
    """
    print("AI Ассистент запущен!")
    print("Введите ваш запрос. Для выхода напишите 'exit' или 'quit'.")

    while True:
        try:
            user_prompt = input("Вы: ")

            if user_prompt.lower() in ["exit", "quit"]:
                print("До свидания! Ассистент завершает работу.")
                break

            ai_response = get_ai_response(user_prompt)

            print("\nAI Ассистент:")
            print("="*20)
            print(ai_response)
            print("="*20)
            print()

        except KeyboardInterrupt:
            print("\nДо свидания! Ассистент завершает работу.")
            break
        except Exception as e:
            print(f"Произошла ошибка: {e}")
            break

if __name__ == "__main__":
    main()